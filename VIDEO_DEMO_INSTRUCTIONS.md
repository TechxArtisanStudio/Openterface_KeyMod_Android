# Orientation Demo Video - Production Guide

**Date:** 2026-03-20  
**Status:** Frames Created, Ready for Video Production  

---

## ✅ What's Ready

### Video Frames Created:
```
/tmp/portrait_frame.png   (51KB) - Portrait mode mockup
/tmp/landscape_frame.png  (51KB) - Landscape mode mockup  
/tmp/rotation_frame.png   (12KB) - Rotation transition
```

### Video Script:
```
/home/bbot/projects/Openterface_KeyMod_Android/video_script.md
```

---

## 🎥 Option 1: Quick Screen Recording (Recommended - 5 minutes)

### Using Android Device/Emulator:

**1. Install APK on device:**
```bash
adb install ~/projects/Openterface_KeyMod_Android/app/build/outputs/apk/debug/KeyMod-debug.apk
```

**2. Start screen recording:**
```bash
# Using scrcpy (recommended)
scrcpy --record orientation-demo.mp4 --record-audio

# Or using Android Studio Device Manager
# Right-click device → Screen Record
```

**3. Demonstrate features (60 seconds):**
```
0:00 - Open app (portrait mode)
0:10 - Show 2×3 grid layout
0:20 - Rotate device to landscape
0:30 - Show 3×2 grid layout
0:40 - Open settings (show rotation works)
0:50 - Close app
1:00 - End recording
```

**4. Stop recording:**
```bash
# Press Ctrl+C in terminal
# Or click Stop in Android Studio
```

**5. Upload to GitHub:**
```bash
cp orientation-demo.mp4 ~/projects/Openterface_KeyMod_Android/
cd ~/projects/Openterface_KeyMod_Android/
git add orientation-demo.mp4
git commit -m "docs: Add orientation demo video"
git push origin main
```

---

## 🎬 Option 2: Professional Video (30 minutes)

### Using DaVinci Resolve / Shotcut / OpenShot:

**1. Import frames:**
- portrait_frame.png (5 seconds)
- rotation_frame.png (2 seconds)
- landscape_frame.png (5 seconds)

**2. Add transitions:**
- Cross dissolve between frames
- Zoom effect on rotation

**3. Add text overlays:**
- "Portrait Mode - 2×3 Grid"
- "Smooth Auto-Rotation"
- "Landscape Mode - 3×2 Grid"
- "iOS Feature Parity Achieved"

**4. Add background music:**
- Upbeat tech/corporate track
- Low volume (doesn't overpower)

**5. Export settings:**
```
Resolution: 1920×1080 (Full HD)
Format: MP4 (H.264)
Frame Rate: 30 fps
Bitrate: 8-10 Mbps
Audio: AAC, 128 kbps
```

---

## 📱 Option 3: Animated GIF (2 minutes)

### Using ImageMagick:

```bash
cd /tmp

# Create animated GIF
magick -delay 150 \
  portrait_frame.png portrait_frame.png portrait_frame.png \
  rotation_frame.png rotation_frame.png \
  landscape_frame.png landscape_frame.png landscape_frame.png \
  -loop 0 \
  -resize 800x800 \
  orientation_demo.gif

# Upload to GitHub
cp orientation_demo.gif ~/projects/Openterface_KeyMod_Android/
cd ~/projects/Openterface_KeyMod_Android/
git add orientation_demo.gif
git commit -m "docs: Add orientation demo GIF"
git push origin main
```

**Result:** 8-second looping animation showing rotation

---

## 🎞️ Option 4: FFmpeg Video (if fixed)

### Once FFmpeg is working:

```bash
cd /tmp

# Create clips
ffmpeg -y -loop 1 -i portrait_frame.png -t 5 -c:v libx264 -pix_fmt yuv420p portrait_clip.mp4
ffmpeg -y -loop 1 -i rotation_frame.png -t 2 -c:v libx264 -pix_fmt yuv420p rotation_clip.mp4
ffmpeg -y -loop 1 -i landscape_frame.png -t 5 -c:v libx264 -pix_fmt yuv420p landscape_clip.mp4

# Concatenate clips
ffmpeg -y \
  -i portrait_clip.mp4 \
  -i rotation_clip.mp4 \
  -i landscape_clip.mp4 \
  -filter_complex "[0:v][1:v][2:v]concat=n=3:v=1:a=0[outv]" \
  -map "[outv]" \
  -c:v libx264 -pix_fmt yuv420p \
  orientation_demo.mp4

# Upload
cp orientation_demo.mp4 ~/projects/Openterface_KeyMod_Android/
```

---

## 📊 Current Assets

### Frames (Ready to Use):
- ✅ portrait_frame.png (1080×1920)
- ✅ landscape_frame.png (1920×1080)
- ✅ rotation_frame.png (1080×1080)

### Documentation:
- ✅ video_script.md (complete script)
- ✅ ORIENTATION_SUPPORT_ADDED.md (feature docs)

### APK:
- ✅ KeyMod-debug.apk (9.0MB)
- ✅ Ready for screen recording

---

## 🎯 Recommended Approach

**For Quick Demo (Today):**
1. Use **Option 1** (Screen recording with real device)
2. Record for 60 seconds
3. Upload to GitHub releases
4. Share with team

**For Professional Demo (This Week):**
1. Use **Option 2** (Video editing software)
2. Add music and professional transitions
3. Upload to YouTube
4. Embed in README

---

## 📹 Upload Locations

### GitHub Releases:
```bash
# Create release
gh release create v1.0.0 \
  --title "KeyMod Android v1.0.0" \
  --notes "Orientation support added" \
  orientation-demo.mp4
```

### GitHub Wiki:
```markdown
# Orientation Demo

![Orientation Demo](./orientation_demo.gif)

Or watch the full video: [orientation-demo.mp4](./orientation-demo.mp4)
```

### YouTube (Optional):
- Upload video (1080p)
- Title: "KeyMod Android - Portrait & Landscape Support"
- Description: Link to GitHub repo
- Tags: android, kvm, openterface, orientation

---

## 🎬 Storyboard

```
[0:00-0:05]   Title Card
              "KeyMod Orientation Demo"

[0:05-0:15]   Portrait Mode
              Show 2×3 grid layout
              Tap on different cards

[0:15-0:20]   Rotation Transition
              Device rotates
              Smooth animation

[0:20-0:30]   Landscape Mode
              Show 3×2 grid layout
              Tap on different cards

[0:30-0:40]   Settings Screen
              Show settings in portrait
              Rotate to landscape

[0:40-0:50]   Side-by-Side Comparison
              Portrait | Landscape
              Both layouts visible

[0:50-1:00]   End Card
              "iOS Parity Achieved"
              GitHub link
```

---

## 🎵 Music Suggestions

**Free/Creative Commons:**
- "Tech Corporate" by Bensound
- "Innovation" by Bensound  
- "Technology" by Alex-Productions
- "Digital Technology" by MusicLFiles

**Where to find:**
- https://www.bensound.com
- https://pixabay.com/music/
- https://www.youtube.com/audiolibrary

---

## ✅ Next Steps

**Immediate (Choose One):**
1. [ ] Record screen with real device (5 min)
2. [ ] Create animated GIF (2 min)
3. [ ] Use video editing software (30 min)

**After Video Created:**
1. [ ] Upload to GitHub releases
2. [ ] Add to README.md
3. [ ] Share with team
4. [ ] Post on social media (optional)

---

## 📞 Support

**Tools Documentation:**
- scrcpy: https://github.com/Genymobile/scrcpy
- DaVinci Resolve: https://www.blackmagicdesign.com/products/davinciresolve
- Shotcut: https://shotcut.org/
- OpenShot: https://www.openshot.org/

**Contact:**
- Email: kevin@techxartisan.com
- Repository: https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android

---

**Last Updated:** 2026-03-20  
**Frames:** ✅ Created  
**Video:** ⏳ Ready to produce  
**APK:** ✅ Ready for recording  

---

*Generated by OpenClaw Assistant 🦾*
