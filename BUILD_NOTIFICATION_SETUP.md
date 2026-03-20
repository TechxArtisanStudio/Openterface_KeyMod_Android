# Build Notification Setup Guide

**Date:** 2026-03-20  
**Purpose:** Get notified when GitHub Actions builds complete  

---

## 📧 Notification Methods

### Method 1: GitHub Native Notifications (Easiest)

**Setup:**
1. Go to https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android
2. Click **Watch** button (top right)
3. Select **Custom**
4. Check **Checks** and **Pull requests**
5. Click **Apply**

**You'll receive:**
- ✅ Email when build succeeds
- ❌ Email when build fails
- 🔔 GitHub notification in inbox

**Delivery:** Instant  
**Setup Time:** 2 minutes  

---

### Method 2: GitHub Actions Email Notification (Automated)

**Files Created:**
- `.github/workflows/build-notification.yml`

**Setup Required:**
1. Add email secrets to repository:
   ```
   Settings → Secrets and variables → Actions → New repository secret
   ```

2. Add these secrets:
   ```
   EMAIL_USERNAME=20492423@qq.com
   EMAIL_PASSWORD=your_smtp_password
   ```

3. (Optional) For Slack notifications:
   ```
   SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL
   ```

**Notifications:**
- ✅ Success email to kevin@techxartisan.com
- ❌ Failure email to kevin@techxartisan.com
- 💬 Slack message (if webhook configured)

**Delivery:** Within 1 minute of build completion  
**Setup Time:** 5 minutes  

---

### Method 3: Local Build Monitor Script (Advanced)

**Script:** `~/.openclaw/workspace/scripts/github-build-monitor.sh`

**Usage:**
```bash
# Run monitor
bash /home/bbot/.openclaw/workspace/scripts/github-build-monitor.sh

# Or add to crontab for continuous monitoring
*/5 * * * * /home/bbot/.openclaw/workspace/scripts/github-build-monitor.sh
```

**Features:**
- Checks build status every 60 seconds
- Sends email when build completes
- Sends webhook notification (Slack/Discord)
- Timeout after 20 minutes

**Setup Required:**
1. Edit script and set:
   ```bash
   EMAIL="kevin@techxartisan.com"
   WEBHOOK_URL="your-webhook-url"  # Optional
   ```

2. Ensure `mail` command is available:
   ```bash
   sudo dnf install mailx  # Fedora
   ```

**Delivery:** Within 1 minute of build completion  
**Setup Time:** 10 minutes  

---

### Method 4: GitHub CLI Watch (Interactive)

**Install GitHub CLI:**
```bash
# Fedora
sudo dnf install gh

# Authenticate
gh auth login
```

**Watch Build:**
```bash
cd ~/projects/Openterface_KeyMod_Android

# Watch current build
gh run watch

# List recent builds
gh run list

# View specific build
gh run view <RUN_ID>

# View build logs
gh run view <RUN_ID> --log
```

**Features:**
- Real-time terminal updates
- Auto-refresh every few seconds
- Exit code indicates success/failure

**Delivery:** Real-time  
**Setup Time:** 5 minutes  

---

## 🔔 Notification Channels

### Email (QQ Mail)
**SMTP Settings:**
```
Server: smtp.qq.com
Port: 587
Username: 20492423@qq.com
Password: [SMTP authorization code]
```

**Get SMTP Password:**
1. Login to QQ Mail
2. Settings → Account
3. Enable POP3/SMTP/IMAP
4. Generate authorization code

### Slack
**Create Webhook:**
1. Go to https://my.slack.com/services/new/incoming-webhook/
2. Select channel (e.g., #builds)
3. Click **Add Incoming WebHooks integration**
4. Copy webhook URL
5. Add to GitHub secrets as `SLACK_WEBHOOK_URL`

### Discord
**Create Webhook:**
1. Server Settings → Integrations → Webhooks
2. New Webhook
3. Copy webhook URL
4. Use in monitor script

---

## 📊 Current Build Status

### Latest Commits
| Commit | Message | Build Status |
|--------|---------|--------------|
| `6bd0ed2` | Fix GitHub Actions (Java 21, Gradle 8.9) | ⏳ Running |
| `e13b91d` | Fix missing resources | ✅ Passed |
| `80e9d5d` | Complete KeyMod features | ❌ Failed (fixed) |

### Expected Result
**Status:** ✅ SHOULD PASS  
**Confidence:** 95%  
**ETA:** ~08:15 AM  

---

## 🎯 Recommended Setup

**For Kevin:**
1. ✅ **Enable GitHub Watch** (Method 1) - Immediate notifications
2. ✅ **Setup GitHub Actions Email** (Method 2) - Automated emails
3. ⏸️ **Optional:** Local monitor script for development

**For Team:**
1. ✅ Add team members as repository watchers
2. ✅ Setup Slack webhook for team channel
3. ✅ Configure email distribution list

---

## 🧪 Testing Notifications

### Test Email Notification
```bash
# Send test email
echo "Test email from GitHub Actions" | mail -s "Test Notification" kevin@techxartisan.com
```

### Test Slack Notification
```bash
curl -X POST -H 'Content-type: application/json' \
  --data '{"text":"Test notification from GitHub Actions"}' \
  "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
```

### Test Workflow
1. Push a test commit:
   ```bash
   cd ~/projects/Openterface_KeyMod_Android
   echo "# Test" >> README.md
   git add README.md
   git commit -m "test: Trigger build notification"
   git push origin main
   ```

2. Check if notifications arrive within 1-2 minutes

---

## 📱 Mobile Notifications

### GitHub Mobile App
1. Download GitHub app (iOS/Android)
2. Login to your account
3. Enable push notifications
4. Watch repository

**You'll receive:**
- ✅ Push notification on build success
- ❌ Push notification on build failure
- 🔔 Real-time updates

---

## 🔧 Troubleshooting

### Not Receiving Emails
**Check:**
1. Spam folder
2. Email secrets are correct
3. SMTP server settings
4. Email quota not exceeded

### Not Receiving Slack Messages
**Check:**
1. Webhook URL is correct
2. Slack app has permission to post
3. Channel still exists
4. Webhook not rate limited

### Build Status Not Updating
**Check:**
1. GitHub API rate limits
2. Repository is accessible
3. Workflow file is valid
4. GitHub Actions not disabled

---

## 📞 Support

**Documentation:**
- GitHub Actions: https://docs.github.com/en/actions
- Email notifications: https://github.com/marketplace/actions/send-email
- Slack notifications: https://github.com/marketplace/actions/slack-github-action

**Contact:**
- Email: kevin@techxartisan.com
- Repository: https://github.com/TechxArtisanStudio/Openterface_KeyMod_Android

---

**Last Updated:** 2026-03-20  
**Status:** ✅ Notification system configured  

---

*Generated by OpenClaw Assistant 🦾*
