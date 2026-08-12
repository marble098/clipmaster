#!/system/bin/sh
# ClipMaster KernelSU / Magisk Module Installer
# This script runs during module installation.

SKIPUNZIP=0

# Print module info
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  ClipMaster Clipboard Manager"
ui_print "  System-level installation"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Verify architecture
ARCH=$(getprop ro.product.cpu.abi)
ui_print "• Device arch: $ARCH"

# Verify Android version (requires API 26+)
API=$(getprop ro.build.version.sdk)
if [ "$API" -lt 26 ]; then
    ui_print "✗ Android 8.0+ required (detected API $API)"
    abort "Unsupported Android version"
fi
ui_print "• Android API: $API ✓"

# The module zip structure places the APK at:
#   system/app/ClipMaster/ClipMaster.apk
# KernelSU/Magisk will overlay this onto /system/app/ClipMaster/

# Set permissions
set_perm_recursive "$MODPATH/system/app" 0 0 0755 0644

ui_print "• APK installed to /system/app/ClipMaster/"
ui_print ""
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  Installation complete."
ui_print "  Reboot to activate."
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
