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

```Action	com.arthur.freeollee.SEND_VALUE```
```Extra	value:*yourvalue*```
```Package	com.arthur.freeollee```
```Target	Broadcast Receiver```

  /!\   If bluetooth was disconnected ('lap' icon not showing on watch), long-press the bottom-right button twice to reenable bluetooth and then sync the glucose data
  
 

# TODO
- Improve Bluetooth interractions to save battery life
- Create ways to prevent wrong values behind on the watch (out-of-sync issues)
- Put delta values
- Notifications for low or high glucose


# Other

This app could be a way to show data from many apps (weather, stock, crypto, reminders, even notifications). The MainActivity.kt could easily be adapted. You are absolutely free to fork this repository and modify it to do whatever you want. Keep in mind that the Ollee Watch roadmap (https://www.olleewatch.com/blog/feature-roadmap-october-2025) shows that many updates are coming, especially opening the app to allow the creation of 3rd party watchfaces


