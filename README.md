# Launchctl configuration example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
  <dict>
    <key>Label</key>
    <string>net.mlorber.autocommit.pap</string>
    <key>ProgramArguments</key>
    <array>
		<string>/usr/local/bin/python2</string>
        <string>/Users/mlo/git/autocommit/autocommit</string>
        <string>-d</string>
        <string>/Users/mlo/git/pap</string>
        <string>-g</string>
        <string>/usr/local/bin/git</string>
    </array>
    <key>RunAtLoad</key>
    <true />
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>/Users/mlo/work/autocommit/pap.log</string>
    <key>StandardErrorPath</key>
    <string>/Users/mlo/work/autocommit/pap-error.log</string>
  </dict>
</plist>
```
