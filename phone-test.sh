#!/bin/bash
# Lockedintool phone test (owner, 12 Nov 2026: 'Yes build the phone test'): installs the test-only build on an Android
# emulator and plays through real situations on a phone - Station 3, the Pages, the dial pad upright and sideways, the
# lock screen. Every step writes a line to report.txt and a screenshot; nothing here touches a real account or server
# (the test phone has no internet - the app runs its own built-in screens).
set -u
P=com.story.launcher
OUT=phone-report; mkdir -p $OUT
R=$OUT/report.txt; : > $R
log(){ echo "$*" | tee -a $R; }
# Screenshots / films are taken by the emulator itself, from outside the phone (taking them inside the slow test phone
# crashed it).
shot(){ timeout 30 adb emu screenrecord screenshot "$PWD/$OUT/$1.png" >/dev/null 2>&1; [ -s $OUT/$1.png ] && log "  screenshot: $1.png" || log "  (no screenshot $1)"; }
js(){ timeout 15 node cdp.mjs "$1" "$2" 2>/dev/null || echo "(no answer)"; }
tap(){ adb shell input tap $1 $2; sleep 1.2; }
size(){ adb shell wm size | tail -1 | grep -o '[0-9]*x[0-9]*'; }
rot(){ adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation $1; sleep 2.5; }
cur_rot(){ local d; d=$(adb shell dumpsys window displays | grep -m1 -o 'cur=[0-9]*x[0-9]*' | grep -o '[0-9]*x[0-9]*'); [ "${d%x*}" -gt "${d#*x}" ] 2>/dev/null && echo "sideways ($d)" || echo "upright ($d)"; }
targets(){ curl -s http://127.0.0.1:9222/json | grep -o '"url": *"[^"]*"' | tee -a $R; }
forward(){ local pid; pid=$(adb shell pidof $P | tr -d '\r' | awk '{print $1}'); adb forward --remove-all >/dev/null 2>&1; adb forward tcp:9222 localabstract:webview_devtools_remote_$pid >/dev/null && log "  devtools on pid $pid"; }
step(){ [ "$(adb get-state 2>/dev/null)" = "device" ] || { log ""; log "TEST PHONE LOST (emulator crashed) - stopping this attempt"; exit 3; }; log ""; log "== $*"; }
# Films the screen while something happens and reports black frames (#269 - the owner's video: black / the old page
# flashing before the Pages showed). rec_start NAME ... rec_stop NAME
rec_start(){ timeout 15 adb emu screenrecord start --time-limit 12 --fps 15 "$PWD/$OUT/$1.webm" >/dev/null 2>&1; sleep 1; }
rec_stop(){ sleep 1; timeout 20 adb emu screenrecord stop >/dev/null 2>&1; sleep 2
  [ -s $OUT/$1.webm ] || { log "  (no recording)"; return; }
  ffmpeg -v error -y -i $OUT/$1.webm $OUT/$1.mp4 && rm -f $OUT/$1.webm
  ffmpeg -v error -i $OUT/$1.mp4 -vf "fps=10,scale=160:-1,tile=10x5" -frames:v 1 $OUT/$1-frames.png 2>/dev/null && log "  frames: $1-frames.png (10 a second)"
  local b; b=$(ffmpeg -i $OUT/$1.mp4 -vf "blackdetect=d=0.05:pic_th=0.90:pix_th=0.06" -an -f null - 2>&1 | grep -o 'black_duration:[0-9.]*' | tr '\n' ' ')
  [ -n "$b" ] && log "  BLACK on screen: $b" || log "  no black frames"
  rm -f $OUT/$1.mp4; }

step "SETUP"
adb install -r -g app-test.apk | tail -1 | tee -a $R
adb shell svc wifi disable; adb shell svc data disable; adb shell cmd connectivity airplane-mode enable   # no internet: no real servers
adb shell appops set $P SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services $P/$P.StoryAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell cmd notification allow_listener $P/$P.StoryMediaListener
# Second system, switched on, past the first-run screens (the app's own settings file - debug build only).
cat > /tmp/story.xml <<'X'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="enabled" value="true" />
    <boolean name="system_on" value="true" />
    <boolean name="auto_agreed" value="true" />
    <boolean name="in_lit" value="false" />
</map>
X
adb push /tmp/story.xml /data/local/tmp/story.xml >/dev/null; adb shell chmod 644 /data/local/tmp/story.xml
adb shell "run-as $P sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/story.xml shared_prefs/story.xml'" && log "  settings written (Second system, switched on)"
adb shell am start -n $P/.MainActivity >/dev/null; sleep 8
adb shell input keyevent KEYCODE_HOME; sleep 4
W=$(size); WW=${W%x*}; HH=${W#*x}; log "  screen ${WW}x${HH}"
log "  service: $(adb shell dumpsys activity services $P | grep -c OverlayService) (1+ = running)"
forward
targets
# Sign in the test account inside the built-in screens (offline - it never reaches a server), skip the tour.
# Only the Pages and Profile's window need the account; one at a time (the test phone has no graphics chip - reloading
# every window at once took it offline).
SIGNIN="(()=>{ localStorage.setItem('story-msg-account', JSON.stringify({id:'phonetest',token:'t',pw:true,username:'phonetest',name:'Phone Test'})); localStorage.setItem('story-tour','done'); localStorage.setItem('story-profile', JSON.stringify({name:'Phone Test',username:'phonetest'})); return 'ok'; })()"
js pages "$SIGNIN" | tee -a $R
js pages "location.reload(),'reloading'" >/dev/null; sleep 12
js profile "location.reload(),'reloading'" >/dev/null; sleep 12
log "  test phone still there: $(adb get-state 2>&1)"
shot 00-home

step "PAGES OPEN FROM THE BOTTOM SWITCH (Second system) - filmed"
B=""
rec_start open-pages
for dy in 10 24 40 60; do
  tap $((WW/2)) $((HH-dy))
  timeout 25 adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  B=$(adb shell cat /sdcard/u.xml | grep -o '<node[^>]*text="Lockedintool"[^>]*package="'$P'"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1 | grep -o '[0-9]\+' | tr '\n' ' ')
  [ -n "$B" ] && { log "  the Apps | Lockedintool switch opened (tap $dy px from the bottom)"; break; }
done
[ -z "$B" ] && log "  FAIL: the bottom switch did not open"
shot 01-switch
set -- $B; [ $# -ge 4 ] && tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
sleep 2
rec_stop open-pages
log "  pages: $(js pages "JSON.stringify({cur:(window.state||{}).current, vis:document.visibilityState, gate:!!document.querySelector('.lit-auth')})")"
shot 02-pages

step "PAGES OPEN (the way a home screen widget opens them) - filmed (#269: no black, no old page)"
adb shell input keyevent KEYCODE_HOME; sleep 2
rec_start open-pages-widget
adb shell am start-foreground-service -n $P/.OverlayService -a com.story.launcher.OPEN_PAGE --es page dashboard >/dev/null 2>&1
sleep 3
rec_stop open-pages-widget
log "  pages: $(js pages "JSON.stringify({vis:document.visibilityState, dash:!!document.querySelector('.story-screen'), gate:!!document.querySelector('.lit-auth')})")"
shot 02b-pages-open
log "  then Money, closed, and opened again on the Dashboard - filmed (the old page must never flash):"
js pages "window.storyShow&&window.storyShow('money',false),'ok'" >/dev/null; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 3
rec_start reopen-pages
adb shell am start-foreground-service -n $P/.OverlayService -a com.story.launcher.OPEN_PAGE --es page dashboard >/dev/null 2>&1
sleep 3
rec_stop reopen-pages

step "PAGES STAY UPRIGHT WHEN THE PHONE TURNS (#264)"
rot 1
log "  screen with the Pages up: $(cur_rot) - expected upright"
shot 03-pages-sideways
rot 0

step "STATION 3 ON THE PAGES"
js pages "window.storyShow&&window.storyShow('money',false),'ok'" >/dev/null; sleep 2
tap $((WW-20)) $((HH-20))
log "  s3: $(js s3 "JSON.stringify({open:document.body.classList.contains('s3-only'), pages:document.body.classList.contains('s3-pages')})")"
shot 04-station3-money

step "DIAL PAD UPRIGHT"
js s3 "window.StoryNative.toggleDialpad(),'ok'" >/dev/null; sleep 2
shot 05-dialpad-upright
log "  dial: $(js dial "JSON.stringify((()=>{const f=document.querySelector('.station3-dialpad-float'); const b=f&&f.getBoundingClientRect(); return {z:getComputedStyle(document.body).getPropertyValue('--dial-z'), w:b&&b.width, h:b&&b.height, room:window.__storyDialRoom}; })())")"

step "DIAL PAD SIDEWAYS OVER AN APP (#255 / #256 / #265 / #266)"
js s3 "window.StoryNative.toggleDialpad(),'ok'" >/dev/null; sleep 1
tap $((WW-20)) $((HH-20))                                  # close Station 3
adb shell am start -a android.settings.SETTINGS >/dev/null; sleep 3   # another app in front, Pages step aside
rot 1
log "  screen over Settings: $(cur_rot) - expected sideways"
W2=$(size); SW=$(adb shell dumpsys window displays | grep -m1 -o 'cur=[0-9]*x[0-9]*' | grep -o '[0-9]*x[0-9]*'); log "  display now $SW"
SWW=${SW%x*}; SHH=${SW#*x}
tap $((SWW-20)) $((SHH-20)); sleep 1
js s3 "window.StoryNative.toggleDialpad(),'ok'" >/dev/null; sleep 2.5
log "  dial: $(js dial "JSON.stringify((()=>{const f=document.querySelector('.station3-dialpad-float'); const b=f&&f.getBoundingClientRect(); return {z:getComputedStyle(document.body).getPropertyValue('--dial-z'), w:b&&Math.round(b.width), h:b&&Math.round(b.height), room:window.__storyDialRoom, screenH:screen.height}; })())")"
shot 06-dialpad-sideways
js s3 "window.StoryNative.toggleDialpad(),'ok'" >/dev/null; sleep 1
tap $((SWW-20)) $((SHH-20))
rot 0

step "LOCK SCREEN: STATION 3 WITHOUT UNLOCKING"
adb shell locksettings set-pin 1234 >/dev/null 2>&1
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input keyevent 26; sleep 3; adb shell input keyevent 224; sleep 4
log "  locked: $(adb shell dumpsys window | grep -m1 -o 'mDreamingLockscreen=[a-z]*\|isKeyguardShowing=[a-z]*\|mShowingLockscreen=[a-z]*')"
shot 07-lockscreen
tap $((WW-20)) $((HH-20)); sleep 1
shot 08-lockscreen-station3
js s3 "window.StoryNative.toggleMessages&&window.StoryNative.toggleMessages(),'ok'" >/dev/null; sleep 2.5
log "  tapping Messages while locked must ask for the phone's unlock (#267) - nothing of Messages may show yet"
shot 09-lockscreen-messages-asks-unlock
log "  in front now: $(adb shell dumpsys window | grep -m1 -o 'mCurrentFocus=[^ ]* [^ ]* [^}]*')"
log "  unlock asked: $(timeout 15 adb logcat -d | grep -E 'IdentityActivity|requestDismissKeyguard|KeyguardDismiss' | tail -3)"
log "  profile window shown before unlock: $(js profile "JSON.stringify({messagesVisible:!!(document.querySelector('.pf-msgs')&&!document.querySelector('.pf-msgs').hidden&&document.visibilityState==='visible')})")"
sleep 1.5; adb shell input text 1234; adb shell input keyevent 66; sleep 4
log "  after the PIN: $(js profile "JSON.stringify({page:(document.querySelector('.pf-msgs')&&!document.querySelector('.pf-msgs').hidden)?'messages':'other', vis:document.visibilityState, gate:!!document.querySelector('#story-profile .lit-auth')})")"
shot 10-after-unlock-messages
adb shell locksettings clear --old 1234 >/dev/null 2>&1

step "PROBLEMS SEEN"
timeout 20 adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: |com.story.launcher.*(Exception|Error)" | head -40 | tee -a $R
log "  Android blocking Lockedintool's screens from the background:"
timeout 20 adb logcat -d | grep -iE "Background activity (launch|start) blocked|Abort background activity|BAL_BLOCK" | grep -i story | head -10 | tee -a $R
log "  console errors in the screens:"
js all "JSON.stringify(window.__litErrors||[])" | tee -a $R
log ""; log "DONE"
