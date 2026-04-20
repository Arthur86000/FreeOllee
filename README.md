# FreeOllee

FreeOllee is an apk that allow to send custom data to an Ollee Watch



# Install
 
- Download the apk in the release section (https://github.com/Arthur86000/FreeOllee/releases/)
- Install the app and open it
- Tap on the "Request Permissions" button and accept
- Tap on the "Select a watch" button and choose your Ollee Watch

# How to use

Send a broadcast intent to this app
- From adb : ```adb shell am broadcast -a com.arthur.freeollee.SEND_VALUE --es value "value"```
- From tasker : Create

<pre>Action	com.arthur.freeollee.SEND_VALUE
Extra	value:*yourvalue*
Package	com.arthur.freeollee
Target	Broadcast Receiver</pre>

  /!\   If bluetooth was disconnected ('lap' icon not showing on watch), long-press the bottom-right button twice to reenable bluetooth and then sync the glucose data
  
 

# TODO
- Create ways to prevent wrong values behind on the watch (out-of-sync issues)
- Alarm on notification received



