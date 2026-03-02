<?php

/**
 * Routes to add to web.php for Yammbo Music app pricing page.
 *
 * Add these lines inside web.php (outside any middleware group or
 * inside a group without auth middleware so the pricing page is public).
 */

// ── Yammbo Music App Pricing ──────────────────────────────────
Route::prefix('app-music')->group(function () {
    // Pricing page (plan listing)
    Route::get('/pricing', [App\Http\Controllers\MusicAppPricingController::class, 'show']);
    Route::post('/pricing/login', [App\Http\Controllers\MusicAppPricingController::class, 'login']);
    Route::get('/pricing/checkout/{planId}', [App\Http\Controllers\MusicAppPricingController::class, 'checkout']);

    // Stripe payments
    Route::post('/payment/create-session', [App\Http\Controllers\MusicAppPricingController::class, 'createCheckoutSession']);
    Route::get('/payment/success', [App\Http\Controllers\MusicAppPricingController::class, 'paymentSuccess']);
    Route::get('/payment/cancel', [App\Http\Controllers\MusicAppPricingController::class, 'paymentCancel']);

    // PayPal payments
    Route::post('/payment/paypal/create-order', [App\Http\Controllers\MusicAppPricingController::class, 'createPayPalOrder']);
    Route::get('/payment/paypal/success', [App\Http\Controllers\MusicAppPricingController::class, 'paypalSuccess']);
    Route::get('/payment/paypal/cancel', [App\Http\Controllers\MusicAppPricingController::class, 'paypalCancel']);
});

// Redirect /app-pricing to /app-music/pricing (the URL used by the Android app)
Route::get('/app-pricing', function (\Illuminate\Http\Request $request) {
    return redirect('/app-music/pricing?' . $request->getQueryString());
});
