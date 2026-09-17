import sys
import xml.etree.ElementTree as ET
from pathlib import Path

path = Path(sys.argv[1])
ANDROID = 'http://schemas.android.com/apk/res/android'
TOOLS = 'http://schemas.android.com/tools'
ET.register_namespace('android', ANDROID)
ET.register_namespace('tools', TOOLS)
A = lambda name: '{%s}%s' % (ANDROID, name)

tree = ET.parse(path)
root = tree.getroot()
app = root.find('application')
if app is None:
    raise SystemExit('application not found')

found = False
for activity in app.findall('activity'):
    if activity.get(A('name')) == 'com.samp.mobile.game.SAMP':
        found = True
        for filt in list(activity.findall('intent-filter')):
            actions = [x.get(A('name')) for x in filt.findall('action')]
            cats = [x.get(A('name')) for x in filt.findall('category')]
            if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in cats:
                activity.remove(filt)
if not found:
    raise SystemExit('original SAMP activity not found')

diag = ET.SubElement(app, 'activity', {
    A('name'): 'com.samp.mobile.diag.DiagnosticsActivity',
    A('exported'): 'true',
    A('screenOrientation'): 'landscape',
    A('theme'): '@android:style/Theme.Material.NoActionBar.Fullscreen'
})
filt = ET.SubElement(diag, 'intent-filter')
ET.SubElement(filt, 'action', {A('name'): 'android.intent.action.MAIN'})
ET.SubElement(filt, 'category', {A('name'): 'android.intent.category.LAUNCHER'})

ET.SubElement(app, 'activity', {
    A('name'): 'com.samp.mobile.diag.GtasaProbeActivity',
    A('exported'): 'false',
    A('process'): ':diag_gtasa',
    A('screenOrientation'): 'landscape',
    A('theme'): '@android:style/Theme.Material.NoActionBar.Fullscreen'
})
ET.SubElement(app, 'activity', {
    A('name'): 'com.samp.mobile.diag.FullNativeProbeActivity',
    A('exported'): 'false',
    A('process'): ':diag_fullnative',
    A('screenOrientation'): 'landscape',
    A('theme'): '@android:style/Theme.Material.NoActionBar.Fullscreen'
})

tree.write(path, encoding='utf-8', xml_declaration=True)
