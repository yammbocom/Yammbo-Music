<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>{{ $success ? 'Pago Exitoso' : 'Pago Cancelado' }} - Yammbo Music</title>
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
            padding: 40px 24px;
            width: 100%;
            max-width: 450px;
            border: 1px solid #2a2a2a;
            text-align: center;
        }
        .icon {
            font-size: 72px;
            margin-bottom: 20px;
            line-height: 1;
        }
        .icon-success { color: #4caf50; }
        .icon-error { color: #ff6b6b; }
        h1 {
            color: #fff;
            font-size: 24px;
            font-weight: 700;
            margin-bottom: 12px;
        }
        .message {
            color: #888;
            font-size: 16px;
            margin-bottom: 28px;
            line-height: 1.5;
        }
        .subscription-info {
            background: #111;
            padding: 20px;
            border-radius: 16px;
            margin-bottom: 28px;
            border: 1px solid #2a2a2a;
        }
        .subscription-info .plan-name {
            font-size: 20px;
            font-weight: 700;
            color: #fff;
            margin-bottom: 6px;
        }
        .subscription-info .plan-expires {
            color: #666;
            font-size: 14px;
        }
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
            margin-bottom: 12px;
            -webkit-appearance: none;
        }
        .btn:active { transform: scale(0.98); }
        .btn-success {
            background: #fff;
            color: #000;
            font-size: 18px;
            padding: 18px;
        }
        .btn-success:hover { opacity: 0.9; }
        .btn-retry {
            background: #fff;
            color: #000;
        }
        .btn-retry:hover { opacity: 0.9; }
        .btn-outline {
            background: transparent;
            border: 2px solid #333;
            color: #888;
        }
        .btn-outline:hover { border-color: #555; color: #ccc; }
        .note {
            color: #555;
            font-size: 13px;
            margin-top: 16px;
            line-height: 1.4;
        }
    </style>
</head>
<body>
    <div class="container">
        @if($success)
            <div class="icon icon-success">&#10004;</div>
            <h1>Pago Exitoso!</h1>
            <p class="message">{{ $message }}</p>

            @if(isset($plan) && isset($subscription))
                <div class="subscription-info">
                    <div class="plan-name">{{ $plan->name }}</div>
                    <div class="plan-expires">
                        Valido hasta: {{ \Carbon\Carbon::parse($subscription->end_date)->format('d/m/Y') }}
                    </div>
                </div>
            @endif

            <p class="note">Ya puedes cerrar esta pagina y volver a la app. Tu suscripcion se activara automaticamente.</p>

        @else
            <div class="icon icon-error">&#10006;</div>
            <h1>Pago No Completado</h1>
            <p class="message">{{ $message }}</p>

            @if(isset($plan_id) && isset($user_id))
                <a href="{{ url('/app-music/pricing/checkout/' . $plan_id) }}?user_id={{ $user_id }}" class="btn btn-retry">
                    Intentar de Nuevo
                </a>
            @endif

            <a href="{{ url('/app-music/pricing') }}{{ isset($user_id) ? '?user_id=' . $user_id : '' }}" class="btn btn-outline">
                Ver Planes
            </a>
        @endif
    </div>
</body>
</html>
