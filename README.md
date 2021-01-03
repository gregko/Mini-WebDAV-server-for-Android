# Mini WebDAV Server for Android

I originally cloned this from https://github.com/erspicu/Baxermux.server.http, however there
was so much to change, fix, enhance, that I finally created a new Android app project, copied
code fragments Baxermux and continued from there.

Why I needed it: in Android 11 and higher the app private directories under 

    /sdcard/Android/data/package.name.com/files

are so "secure", that noting can access them. When I debug my apps, I often need to manage files
there, create new ones, delete etc. On a physical device it's possible to connect with a USB cable
and manage the files with host file explorer, however in Android emulators, at least at the time
of this writing (Jan. 2021) all I get is "permission denied". No matter if I try to use adb shell,
the mediocre "Device File Explorer" in Android Studio, nothing permits file management there.
Another issue with the "Device File Explorer" in Android Studio is that it does not show hidden 
files and directories (with names starting with a dot), which I need to manage too.

Decided to resurrect this WebDAV server and make it a part of my app, at least in the DEBUG mode.
Now I can access these folders, using any WebDAV client. On my Windows development host I use the
excellent WebDrive product - it mounts the directory exported by the Mini WebDAV server as 
Windows drive, and I can use the native Windows File Explorer, or command prompt, can view and
edit the files as if they were local to my computer.

The root of this Mini WebDAV server is set to app private folder, however a developer could change
it, even set to /sdcard or /storage/emulated/0 and give the app appropriate permissions, maybe 
even declare it to have MANAGE_EXTERNAL_STORAGE permission under Android 11 and handle 
ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION intent action accordingly. Even this would not give a
generic file manager access to any app app private directory though.

Hope it will be useful to other developers as well. I don't recommend making a consumer product from
this, the security issues would need serious consideration.
