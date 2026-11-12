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
shot(){ adb exec-out screencap -p > $OUT/$1.png 2>/dev/null; log "  screenshot: $1.png"; }
js(){ node cdp.mjs "$1" "$2" 2>/dev/null; }
tap(){ adb shell input tap $1 $2; sleep 1.2; }
size(){ adb shell wm size | tail -1 | grep -o '[0-9]*x[0-9]*'; }
rot(){ adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation $1; sleep 2.5; }
cur_rot(){ adb shell dumpsys input | grep -m1 -o 'SurfaceOrientation: [0-9]' | grep -o '[0-9]'; }
forward(){ local pid; pid=$(adb shell pidof $P | tr -d '\r' | awk '{print $1}'); adb forward --remove-all >/dev/null 2>&1; adb forward tcp:9222 localabstract:webview_devtools_remote_$pid >/dev/null && log "  devtools on pid $pid"; }
step(){ log ""; log "== $*"; }

step "SETUP"
adb install -r -g app-test.apk | tail -1 | tee -a $R
adb shell svc wifi disable; adb shell svc data disable          # no internet: the built-in screens, no real servers
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
# Sign in the test account inside the built-in screens (offline - it never reaches a server), skip the tour.
js all "(()=>{ localStorage.setItem('story-msg-account', JSON.stringify({id:'phonetest',token:'t',pw:true,username:'phonetest',name:'Phone Test'})); localStorage.setItem('story-tour','done'); localStorage.setItem('story-profile', JSON.stringify({name:'Phone Test',username:'phonetest'})); location.reload(); return 'ok'; })()" | tee -a $R
sleep 6
shot 00-home

step "PAGES OPEN FROM THE BOTTOM SWITCH (Second system)"
tap $((WW/2)) $((HH-12))
log "  switch: $(adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb shell cat /sdcard/u.xml | grep -o 'text="Lockedintool"' | head -1)"
shot 01-switch
B=$(adb shell cat /sdcard/u.xml | grep -o 'text="Lockedintool"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
set -- $B; [ $# -ge 4 ] && tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
sleep 2
log "  pages: $(js pages "JSON.stringify({cur:(window.state||{}).current, vis:document.visibilityState, gate:!!document.querySelector('.lit-auth')})")"
shot 02-pages

step "PAGES STAY UPRIGHT WHEN THE PHONE TURNS (#264)"
rot 1
log "  rotation with the Pages up: $(cur_rot) (0 = upright - expected 0)"
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
log "  rotation over Settings: $(cur_rot) (expected 1)"
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
log "  profile window shown before unlock: $(js profile "JSON.stringify({messagesVisible:!!(document.querySelector('.pf-msgs')&&!document.querySelector('.pf-msgs').hidden&&document.visibilityState==='visible')})")"
adb shell input text 1234; adb shell input keyevent 66; sleep 3
log "  after the PIN: $(js profile "JSON.stringify({page:(document.querySelector('.pf-msgs')&&!document.querySelector('.pf-msgs').hidden)?'messages':'other', vis:document.visibilityState, gate:!!document.querySelector('#story-profile .lit-auth')})")"
shot 10-after-unlock-messages
adb shell locksettings clear --old 1234 >/dev/null 2>&1

step "PROBLEMS SEEN"
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: |com.story.launcher.*(Exception|Error)" | head -40 | tee -a $R
log "  console errors in the screens:"
js all "JSON.stringify(window.__litErrors||[])" | tee -a $R
log ""; log "DONE"
