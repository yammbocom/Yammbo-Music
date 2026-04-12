# Yammbo Music v21 - Task Plan

## Tasks

### 1. Keystore Update - `complete`
- Copied keystore, updated local.properties with alias=yambo, pass=JF161cg.a

### 2. Fix Music Stopping - `complete`
- Changed cueVideo → loadVideo in onMediaItemTransition() and maybeRecoverPlaybackError()
- Added playFromSecond = 0f reset

### 3. Fix Pricing WebView - `complete`
- Fixed blank screen: removed "; wv" from UA, enabled mixedContentMode, added loading indicator
- Backend confirmed working: Stripe + PayPal live, pricing page renders OK

### 4. Replace App Icon - `complete`
- Copied Downloads/20250318_030814_0000.png to all mipmap densities

### 5. Improve Sharing - `complete`
- Added Yammbo URLs to Song, Artist, Album, Playlist models
- Added LinkType.Yammbo, updated FastShare with 3-option selector

### 6. Bottom Nav Everywhere - `complete`
- Added HorizontalNavigationBar to PageContainer.kt with tab navigation back to home

### 7. Subscription Gate in Settings - `complete`
- Created SubscriptionGateBanner composable
- Added to GeneralSettings, UiSettings, AppearanceSettings, HomeSettings

### 8. Build & Test - `complete`
- APK built successfully: YammboMusic-v21-0.7.69.apk (21.6 MB)
- Copied to Downloads
