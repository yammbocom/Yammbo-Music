<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use App\Models\User;

class MusicAppPricingController extends Controller
{
    private function getStripeKeys()
    {
        $keys = DB::table('settings')
            ->whereIn('name', ['stripe_secretkey', 'stripe_publickey'])
            ->pluck('val', 'name');

        return [
            'secret' => $keys['stripe_secretkey'] ?? null,
            'public' => $keys['stripe_publickey'] ?? null,
        ];
    }

    private function getPayPalClient()
    {
        $keys = DB::table('settings')
            ->whereIn('name', ['paypal_clientid', 'paypal_secretkey'])
            ->pluck('val', 'name');

        $clientId = $keys['paypal_clientid'] ?? null;
        $clientSecret = $keys['paypal_secretkey'] ?? null;

        $environment = new \PayPalCheckoutSdk\Core\ProductionEnvironment($clientId, $clientSecret);
        return new \PayPalCheckoutSdk\Core\PayPalHttpClient($environment);
    }

    private function getPlans()
    {
        return DB::table('plan')
            ->where('status', 1)
            ->orderBy('price', 'asc')
            ->get();
    }

    private function getPlan($id)
    {
        return DB::table('plan')->where('id', $id)->first();
    }

    private function createSubscription($user, $plan, $identifier, $type, $totalAmount)
    {
        $startDate = now();
        if ($plan->duration === 'year') {
            $endDate = now()->addYears($plan->duration_value ?? 1);
        } else {
            $endDate = now()->addMonths($plan->duration_value ?? 1);
        }

        $subscriptionId = DB::table('subscriptions')->insertGetId([
            'plan_id' => $plan->id,
            'user_id' => $user->id,
            'start_date' => $startDate,
            'end_date' => $endDate,
            'status' => 'active',
            'amount' => $plan->price,
            'total_amount' => $totalAmount,
            'name' => $plan->name,
            'identifier' => $identifier,
            'type' => $type,
            'duration' => ($plan->duration_value ?? 1) . ' ' . $plan->duration,
            'level' => $plan->level ?? 1,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        DB::table('subscription_transactions')->insert([
            'subscriptions_id' => $subscriptionId,
            'user_id' => $user->id,
            'amount' => $totalAmount,
            'payment_type' => $type,
            'payment_status' => 'paid',
            'transaction_id' => str_replace([$type . '_'], '', $identifier),
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $user->update(['is_subscribe' => 1]);

        return (object) [
            'id' => $subscriptionId,
            'end_date' => $endDate,
        ];
    }

    /**
     * Show pricing page (GET /app-music/pricing)
     */
    public function show(Request $request)
    {
        $userId = $request->get('user_id');
        $plans = $this->getPlans();

        $user = null;
        if ($userId) {
            $user = User::find($userId);
        }

        $grouped = [];
        foreach ($plans as $plan) {
            $grouped[$plan->duration][] = $plan;
        }

        return view('app-music.pricing', [
            'plans' => $plans,
            'grouped' => $grouped,
            'user' => $user,
            'user_id' => $userId,
        ]);
    }

    /**
     * Login from pricing page (POST /app-music/pricing/login)
     */
    public function login(Request $request)
    {
        $request->validate([
            'email' => 'required|email',
            'password' => 'required|string',
        ]);

        $planId = $request->input('plan_id');

        $user = User::where('email', $request->email)->first();

        if (!$user || !Hash::check($request->password, $user->password)) {
            return redirect('/app-music/pricing')
                ->with('error', 'Credenciales incorrectas');
        }

        return redirect("/app-music/pricing/checkout/{$planId}?user_id={$user->id}");
    }

    /**
     * Checkout page (GET /app-music/pricing/checkout/{planId})
     */
    public function checkout(Request $request, $planId)
    {
        $userId = $request->get('user_id');

        $plan = $this->getPlan($planId);
        if (!$plan) abort(404);

        $user = User::findOrFail($userId);
        $stripeKeys = $this->getStripeKeys();

        return view('app-music.checkout', [
            'plan' => $plan,
            'user' => $user,
            'stripe_public_key' => $stripeKeys['public'],
        ]);
    }

    /**
     * Create Stripe session (POST /app-music/payment/create-session)
     */
    public function createCheckoutSession(Request $request)
    {
        $userId = $request->input('user_id');
        $planId = $request->input('plan_id');

        $plan = $this->getPlan($planId);
        if (!$plan) abort(404);

        $user = User::findOrFail($userId);
        $stripeKeys = $this->getStripeKeys();

        if (!$stripeKeys['secret']) {
            return back()->with('error', 'Error de configuracion de pago. Contacte soporte.');
        }

        \Stripe\Stripe::setApiKey($stripeKeys['secret']);

        try {
            $durationLabel = $plan->duration === 'year' ? 'Anual' : 'Mensual';

            $session = \Stripe\Checkout\Session::create([
                'payment_method_types' => ['card'],
                'line_items' => [[
                    'price_data' => [
                        'currency' => 'usd',
                        'product_data' => [
                            'name' => $plan->name . ' - Yammbo Music',
                            'description' => 'Suscripcion ' . $durationLabel . ' (' . ($plan->duration_value ?? 1) . ' ' . ($plan->duration === 'year' ? 'ano(s)' : 'mes(es)') . ')',
                        ],
                        'unit_amount' => intval($plan->price * 100),
                    ],
                    'quantity' => 1,
                ]],
                'mode' => 'payment',
                'customer_email' => $user->email,
                'metadata' => [
                    'user_id' => $user->id,
                    'plan_id' => $plan->id,
                    'app' => 'yammbo_music',
                ],
                'success_url' => url('/app-music/payment/success') . '?session_id={CHECKOUT_SESSION_ID}',
                'cancel_url' => url('/app-music/payment/cancel') . '?user_id=' . $userId . '&plan_id=' . $planId,
            ]);

            return redirect($session->url);
        } catch (\Exception $e) {
            Log::error('Music Stripe Checkout Error: ' . $e->getMessage());
            return back()->with('error', 'Error al procesar el pago: ' . $e->getMessage());
        }
    }

    /**
     * Stripe success (GET /app-music/payment/success)
     */
    public function paymentSuccess(Request $request)
    {
        $sessionId = $request->get('session_id');

        if (!$sessionId) {
            return view('app-music.payment-result', [
                'success' => false,
                'message' => 'Sesion de pago invalida.',
            ]);
        }

        $stripeKeys = $this->getStripeKeys();
        \Stripe\Stripe::setApiKey($stripeKeys['secret']);

        try {
            $session = \Stripe\Checkout\Session::retrieve($sessionId);

            if ($session->payment_status !== 'paid') {
                return view('app-music.payment-result', [
                    'success' => false,
                    'message' => 'El pago no fue completado.',
                ]);
            }

            $userId = $session->metadata->user_id;
            $planId = $session->metadata->plan_id;

            $plan = $this->getPlan($planId);
            $user = User::findOrFail($userId);

            $identifier = 'stripe_' . $session->payment_intent;

            // Check if already processed
            $existing = DB::table('subscriptions')
                ->where('identifier', $identifier)
                ->first();

            if ($existing) {
                return view('app-music.payment-result', [
                    'success' => true,
                    'message' => 'Tu suscripcion ya fue activada!',
                    'user' => $user,
                    'plan' => $plan,
                    'subscription' => $existing,
                ]);
            }

            $subscription = $this->createSubscription(
                $user,
                $plan,
                $identifier,
                'stripe',
                $session->amount_total / 100
            );

            return view('app-music.payment-result', [
                'success' => true,
                'message' => 'Pago exitoso! Tu suscripcion ha sido activada.',
                'user' => $user,
                'plan' => $plan,
                'subscription' => $subscription,
            ]);

        } catch (\Exception $e) {
            Log::error('Music Stripe Payment Success Error: ' . $e->getMessage());
            return view('app-music.payment-result', [
                'success' => false,
                'message' => 'Error al verificar el pago. Contacte soporte.',
            ]);
        }
    }

    /**
     * Stripe cancel (GET /app-music/payment/cancel)
     */
    public function paymentCancel(Request $request)
    {
        $userId = $request->get('user_id');
        $planId = $request->get('plan_id');

        return view('app-music.payment-result', [
            'success' => false,
            'message' => 'El pago fue cancelado.',
            'user_id' => $userId,
            'plan_id' => $planId,
        ]);
    }

    /**
     * Create PayPal order (POST /app-music/payment/paypal/create-order)
     */
    public function createPayPalOrder(Request $request)
    {
        $userId = $request->input('user_id');
        $planId = $request->input('plan_id');

        $plan = $this->getPlan($planId);
        if (!$plan) abort(404);

        $user = User::findOrFail($userId);
        $client = $this->getPayPalClient();

        $orderRequest = new \PayPalCheckoutSdk\Orders\OrdersCreateRequest();
        $orderRequest->prefer('return=representation');
        $orderRequest->body = [
            'intent' => 'CAPTURE',
            'purchase_units' => [[
                'reference_id' => "music_plan_{$planId}_user_{$userId}",
                'description' => $plan->name . ' - Yammbo Music',
                'amount' => [
                    'currency_code' => 'USD',
                    'value' => number_format($plan->price, 2, '.', ''),
                ],
                'custom_id' => json_encode([
                    'user_id' => $userId,
                    'plan_id' => $planId,
                    'app' => 'yammbo_music',
                ]),
            ]],
            'application_context' => [
                'return_url' => url('/app-music/payment/paypal/success') . "?user_id={$userId}&plan_id={$planId}",
                'cancel_url' => url('/app-music/payment/paypal/cancel') . "?user_id={$userId}&plan_id={$planId}",
                'brand_name' => 'Yammbo Music',
                'user_action' => 'PAY_NOW',
            ],
        ];

        try {
            $response = $client->execute($orderRequest);
            $order = $response->result;

            $approvalUrl = null;
            foreach ($order->links as $link) {
                if ($link->rel === 'approve') {
                    $approvalUrl = $link->href;
                    break;
                }
            }

            if ($approvalUrl) {
                return redirect($approvalUrl);
            }

            return back()->with('error', 'Error al crear orden de PayPal.');
        } catch (\Exception $e) {
            Log::error('Music PayPal Create Order Error: ' . $e->getMessage());
            return back()->with('error', 'Error al procesar PayPal: ' . $e->getMessage());
        }
    }

    /**
     * PayPal success (GET /app-music/payment/paypal/success)
     */
    public function paypalSuccess(Request $request)
    {
        $token = $request->get('token');
        $userId = $request->get('user_id');
        $planId = $request->get('plan_id');

        if (!$token) {
            return view('app-music.payment-result', [
                'success' => false,
                'message' => 'Token de PayPal invalido.',
            ]);
        }

        $client = $this->getPayPalClient();

        try {
            $captureRequest = new \PayPalCheckoutSdk\Orders\OrdersCaptureRequest($token);
            $captureRequest->prefer('return=representation');
            $response = $client->execute($captureRequest);
            $order = $response->result;

            if ($order->status !== 'COMPLETED') {
                return view('app-music.payment-result', [
                    'success' => false,
                    'message' => 'El pago con PayPal no fue completado.',
                ]);
            }

            $plan = $this->getPlan($planId);
            $user = User::findOrFail($userId);

            $captureId = $order->purchase_units[0]->payments->captures[0]->id ?? $token;
            $identifier = 'paypal_' . $captureId;

            // Check if already processed
            $existing = DB::table('subscriptions')
                ->where('identifier', $identifier)
                ->first();

            if ($existing) {
                return view('app-music.payment-result', [
                    'success' => true,
                    'message' => 'Tu suscripcion ya fue activada!',
                    'user' => $user,
                    'plan' => $plan,
                    'subscription' => $existing,
                ]);
            }

            $subscription = $this->createSubscription(
                $user,
                $plan,
                $identifier,
                'paypal',
                $plan->price
            );

            return view('app-music.payment-result', [
                'success' => true,
                'message' => 'Pago exitoso con PayPal! Tu suscripcion ha sido activada.',
                'user' => $user,
                'plan' => $plan,
                'subscription' => $subscription,
            ]);
        } catch (\Exception $e) {
            Log::error('Music PayPal Capture Error: ' . $e->getMessage());
            return view('app-music.payment-result', [
                'success' => false,
                'message' => 'Error al verificar el pago con PayPal. Contacte soporte.',
            ]);
        }
    }

    /**
     * PayPal cancel (GET /app-music/payment/paypal/cancel)
     */
    public function paypalCancel(Request $request)
    {
        $userId = $request->get('user_id');
        $planId = $request->get('plan_id');

        return view('app-music.payment-result', [
            'success' => false,
            'message' => 'El pago con PayPal fue cancelado.',
            'user_id' => $userId,
            'plan_id' => $planId,
        ]);
    }
}
