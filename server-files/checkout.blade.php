<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Checkout - Yammbo Music</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #121212;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
            -webkit-font-smoothing: antialiased;
        }
        .container {
            background: #1e1e1e;
            border-radius: 20px;
            padding: 32px 24px;
            width: 100%;
            max-width: 450px;
            border: 1px solid #2a2a2a;
        }
        .header { text-align: center; margin-bottom: 24px; }
        .header h1 { color: #fff; font-size: 22px; font-weight: 700; }
        .plan-summary {
            background: #111;
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 24px;
            border-left: 4px solid #fff;
        }
        .plan-summary .plan-name { font-size: 20px; font-weight: 700; color: #fff; }
        .plan-summary .plan-price {
            font-size: 32px;
            font-weight: 800;
            color: #fff;
            margin: 8px 0;
        }
        .plan-summary .plan-price span { font-size: 14px; color: #666; font-weight: 400; }
        .plan-summary .plan-detail { color: #666; font-size: 14px; }
        .user-info {
            background: #111;
            border-radius: 12px;
            padding: 14px 16px;
            margin-bottom: 24px;
            font-size: 14px;
            color: #aaa;
            border: 1px solid #2a2a2a;
        }
        .user-info strong { color: #fff; }
        .error-msg {
            background: #2a1010;
            color: #ff6b6b;
            padding: 12px;
            border-radius: 12px;
            margin-bottom: 16px;
            text-align: center;
            border: 1px solid #3a1515;
            font-size: 14px;
        }
        .section-title {
            color: #666;
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 1.5px;
            margin-bottom: 16px;
        }
        .secure-badge {
            display: flex;
            align-items: center;
            gap: 6px;
            color: #4caf50;
            font-size: 13px;
            margin-bottom: 20px;
        }
        .divider {
            display: flex;
            align-items: center;
            margin: 20px 0;
            color: #444;
            font-size: 13px;
        }
        .divider::before, .divider::after {
            content: '';
            flex: 1;
            height: 1px;
            background: #2a2a2a;
        }
        .divider span { padding: 0 12px; }
        .btn {
            width: 100%;
            padding: 16px;
            border: none;
            border-radius: 12px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: transform 0.1s, opacity 0.2s;
            display: block;
            text-align: center;
            text-decoration: none;
            -webkit-appearance: none;
        }
        .btn:active { transform: scale(0.98); }
        .btn-stripe {
            background: #fff;
            color: #000;
            margin-bottom: 4px;
        }
        .btn-stripe:hover { opacity: 0.9; }
        .btn-stripe:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
        .btn-paypal {
            background: #0070ba;
            color: #fff;
            margin-bottom: 4px;
        }
        .btn-paypal:hover { opacity: 0.9; }
        .btn-paypal:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
        .btn-outline {
            background: transparent;
            border: 2px solid #333;
            color: #888;
            margin-top: 16px;
        }
        .btn-outline:hover { border-color: #555; color: #ccc; }
        .payment-icons {
            display: flex;
            gap: 8px;
            margin-bottom: 8px;
            flex-wrap: wrap;
        }
        .payment-icon {
            background: #222;
            border: 1px solid #333;
            border-radius: 6px;
            padding: 4px 10px;
            font-size: 11px;
            color: #888;
            font-weight: 500;
        }
        .spinner {
            display: inline-block;
            width: 18px;
            height: 18px;
            border: 3px solid rgba(0,0,0,0.2);
            border-radius: 50%;
            border-top-color: #000;
            animation: spin 0.8s ease-in-out infinite;
            margin-right: 8px;
            vertical-align: middle;
        }
        .spinner-paypal {
            border-color: rgba(255,255,255,0.3);
            border-top-color: #fff;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Confirmar Suscripcion</h1>
        </div>

        @if(session('error'))
            <div class="error-msg">{{ session('error') }}</div>
        @endif

        <div class="plan-summary">
            <div class="plan-name">{{ $plan->name }}</div>
            <div class="plan-price">
                ${{ number_format($plan->price, 2) }}
                <span>/ {{ $plan->duration === 'year' ? 'Anual' : 'Mensual' }}</span>
            </div>
            <div class="plan-detail">
                Duracion: {{ $plan->duration_value ?? 1 }} {{ $plan->duration === 'year' ? 'ano(s)' : 'mes(es)' }}
            </div>
        </div>

        <div class="user-info">
            <strong>{{ $user->first_name ?? '' }} {{ $user->last_name ?? '' }}</strong><br>
            {{ $user->email }}
        </div>

        <div class="section-title">Metodo de pago</div>
        <div class="secure-badge">&#128274; Pago seguro y encriptado</div>

        <!-- Stripe Payment -->
        <form method="POST" action="{{ url('/app-music/payment/create-session') }}" id="stripeForm" onsubmit="disableBtn('stripeBtn', 'stripe')">
            @csrf
            <input type="hidden" name="user_id" value="{{ $user->id }}">
            <input type="hidden" name="plan_id" value="{{ $plan->id }}">
            <button type="submit" class="btn btn-stripe" id="stripeBtn">
                Pagar con Tarjeta - ${{ number_format($plan->price, 2) }}
            </button>
        </form>

        <div class="payment-icons">
            <div class="payment-icon">Visa</div>
            <div class="payment-icon">Mastercard</div>
            <div class="payment-icon">Amex</div>
        </div>

        <div class="divider"><span>o</span></div>

        <!-- PayPal Payment -->
        <form method="POST" action="{{ url('/app-music/payment/paypal/create-order') }}" id="paypalForm" onsubmit="disableBtn('paypalBtn', 'paypal')">
            @csrf
            <input type="hidden" name="user_id" value="{{ $user->id }}">
            <input type="hidden" name="plan_id" value="{{ $plan->id }}">
            <button type="submit" class="btn btn-paypal" id="paypalBtn">
                Pagar con PayPal - ${{ number_format($plan->price, 2) }}
            </button>
        </form>

        <a href="{{ url('/app-music/pricing') }}?user_id={{ $user->id }}" class="btn btn-outline">
            Cambiar Plan
        </a>
    </div>

    <script>
        function disableBtn(btnId, type) {
            var btn = document.getElementById(btnId);
            var price = '${{ number_format($plan->price, 2) }}';
            setTimeout(function() {
                btn.disabled = true;
                var spinnerClass = type === 'paypal' ? 'spinner spinner-paypal' : 'spinner';
                btn.innerHTML = '<span class="' + spinnerClass + '"></span> Procesando...';
            }, 100);
            setTimeout(function() {
                btn.disabled = false;
                if (type === 'stripe') {
                    btn.innerHTML = 'Pagar con Tarjeta - ' + price;
                } else {
                    btn.innerHTML = 'Pagar con PayPal - ' + price;
                }
            }, 15000);
        }
    </script>
</body>
</html>
