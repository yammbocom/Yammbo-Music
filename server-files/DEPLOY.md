# Deployment: Yammbo Music App Pricing Page

## Files to deploy on server (music.yammbo.com)

Server path: `/home/yammbo/domains/music.yammbo.com/public_html/`

### 1. Controller
Copy `MusicAppPricingController.php` to:
```
app/Http/Controllers/MusicAppPricingController.php
```

### 2. Blade Views
Create directory and copy views:
```bash
mkdir -p resources/views/app-music
cp pricing.blade.php resources/views/app-music/pricing.blade.php
cp checkout.blade.php resources/views/app-music/checkout.blade.php
cp payment-result.blade.php resources/views/app-music/payment-result.blade.php
```

### 3. Routes
Add routes from `routes-to-add.php` to the bottom of `routes/web.php`.

**IMPORTANT**: These routes must be placed BEFORE any catch-all SPA route
(like `Route::get('{any}', ...)`) otherwise they won't work.

### 4. Verify DB tables
The controller uses these tables (same as tv.yammbo.com):
- `plan` - Subscription plans (name, price, duration, duration_value, status, level)
- `subscriptions` - Active subscriptions
- `subscription_transactions` - Payment transaction log
- `settings` - Stripe/PayPal API keys (stripe_secretkey, stripe_publickey, paypal_clientid, paypal_secretkey)

If the music.yammbo.com DB uses different table names (e.g., BeMusic uses `products`
instead of `plan`), update the queries in the controller accordingly.

### 5. Verify dependencies
- `stripe/stripe-php` must be installed (`composer require stripe/stripe-php`)
- `paypalcheckoutsdk` must be installed (`composer require paypal/paypal-checkout-sdk`)

### 6. Clear cache
```bash
php artisan route:cache
php artisan view:clear
php artisan config:cache
```

## URL Flow
1. App opens: `https://music.yammbo.com/app-pricing?user_id=123`
2. Redirects to: `/app-music/pricing?user_id=123`
3. User selects plan → checkout page
4. User pays (Stripe or PayPal)
5. Success page shown → user returns to app

## Testing
```bash
# Check route is registered
php artisan route:list | grep app-music

# Test pricing page loads
curl -s http://localhost/app-music/pricing | head -20

# Test with user_id
curl -s "http://localhost/app-music/pricing?user_id=1" | head -20
```
