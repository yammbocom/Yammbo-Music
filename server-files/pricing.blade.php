<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Planes - Yammbo Music</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #121212;
            min-height: 100vh;
            padding: 24px 16px;
            color: #fff;
            -webkit-font-smoothing: antialiased;
        }
        .header {
            text-align: center;
            margin-bottom: 32px;
        }
        .header-logo {
            width: 64px;
            height: 64px;
            margin: 0 auto 16px;
            background: #fff;
            border-radius: 16px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .header-logo svg { width: 36px; height: 36px; }
        .header h1 {
            font-size: 24px;
            font-weight: 700;
            margin-bottom: 8px;
        }
        .header p {
            color: #888;
            font-size: 15px;
            line-height: 1.4;
        }
        .error-msg {
            background: #2a1010;
            color: #ff6b6b;
            padding: 12px 16px;
            border-radius: 12px;
            margin-bottom: 20px;
            text-align: center;
            max-width: 500px;
            margin-left: auto;
            margin-right: auto;
            border: 1px solid #3a1515;
            font-size: 14px;
        }
        .user-badge {
            display: flex;
            align-items: center;
            gap: 12px;
            background: #1e1e1e;
            border-radius: 12px;
            padding: 12px 16px;
            margin-bottom: 24px;
            max-width: 500px;
            margin-left: auto;
            margin-right: auto;
        }
        .user-avatar {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background: #333;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            font-weight: 700;
            color: #fff;
            flex-shrink: 0;
        }
        .user-info { flex: 1; min-width: 0; }
        .user-name {
            font-size: 14px;
            font-weight: 600;
            color: #fff;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .user-email {
            font-size: 12px;
            color: #888;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .duration-tabs {
            display: flex;
            justify-content: center;
            gap: 8px;
            margin-bottom: 24px;
        }
        .duration-tab {
            padding: 10px 28px;
            border-radius: 24px;
            border: 2px solid #333;
            background: transparent;
            color: #888;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }
        .duration-tab.active {
            background: #fff;
            color: #000;
            border-color: #fff;
        }
        .plans-grid {
            display: none;
            flex-direction: column;
            gap: 16px;
            max-width: 500px;
            margin: 0 auto;
        }
        .plans-grid.active { display: flex; }
        .plan-card {
            background: #1e1e1e;
            border: 2px solid #2a2a2a;
            border-radius: 16px;
            padding: 24px;
            text-align: center;
            transition: border-color 0.2s, transform 0.2s;
        }
        .plan-card.popular {
            border-color: #fff;
            position: relative;
        }
        .plan-card.popular::before {
            content: 'Recomendado';
            position: absolute;
            top: -12px;
            left: 50%;
            transform: translateX(-50%);
            background: #fff;
            color: #000;
            padding: 4px 16px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 700;
        }
        .plan-name {
            font-size: 20px;
            font-weight: 700;
            margin-bottom: 8px;
        }
        .plan-price {
            font-size: 40px;
            font-weight: 800;
            color: #fff;
            line-height: 1;
        }
        .plan-price span {
            font-size: 16px;
            color: #666;
            font-weight: 400;
        }
        .plan-duration {
            color: #666;
            font-size: 14px;
            margin: 8px 0 20px;
        }
        .plan-features {
            text-align: left;
            margin-bottom: 20px;
            padding: 0 8px;
        }
        .plan-features li {
            list-style: none;
            color: #aaa;
            font-size: 14px;
            padding: 4px 0;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .plan-features li::before {
            content: '\2713';
            color: #fff;
            font-weight: 700;
            font-size: 13px;
        }
        .plan-btn {
            display: block;
            width: 100%;
            padding: 14px;
            border-radius: 12px;
            font-size: 16px;
            font-weight: 600;
            text-decoration: none;
            text-align: center;
            cursor: pointer;
            border: none;
            background: #fff;
            color: #000;
            transition: opacity 0.2s;
        }
        .plan-btn:hover { opacity: 0.9; }
        .plan-btn:active { transform: scale(0.98); }

        /* Login overlay */
        .login-overlay {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.9);
            z-index: 100;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .login-overlay.active { display: flex; }
        .login-box {
            background: #1e1e1e;
            border-radius: 16px;
            padding: 32px 24px;
            width: 100%;
            max-width: 400px;
            border: 1px solid #2a2a2a;
        }
        .login-box h2 {
            margin-bottom: 24px;
            color: #fff;
            text-align: center;
            font-size: 20px;
        }
        .form-group { margin-bottom: 16px; }
        .form-group label {
            display: block;
            color: #888;
            font-weight: 500;
            margin-bottom: 6px;
            font-size: 14px;
        }
        .form-group input {
            width: 100%;
            padding: 14px 16px;
            border: 2px solid #333;
            border-radius: 12px;
            font-size: 16px;
            background: #111;
            color: #fff;
            -webkit-appearance: none;
        }
        .form-group input:focus {
            outline: none;
            border-color: #fff;
        }
        .form-group input::placeholder { color: #555; }
        .btn-login {
            width: 100%;
            padding: 14px;
            border: none;
            border-radius: 12px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            background: #fff;
            color: #000;
        }
        .btn-cancel {
            width: 100%;
            padding: 12px;
            border: none;
            background: transparent;
            color: #666;
            font-size: 14px;
            cursor: pointer;
            margin-top: 12px;
        }
        .footer {
            text-align: center;
            margin-top: 32px;
            color: #444;
            font-size: 12px;
        }
    </style>
</head>
<body>
    <div class="header">
        <div class="header-logo">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55C7.79 13 6 14.79 6 17s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z" fill="#000"/>
            </svg>
        </div>
        <h1>Yammbo Music</h1>
        <p>Elige el plan que mejor se adapte a ti</p>
    </div>

    @if($user)
        <div class="user-badge">
            <div class="user-avatar">{{ strtoupper(substr($user->first_name ?? $user->email, 0, 1)) }}</div>
            <div class="user-info">
                <div class="user-name">{{ $user->first_name ?? '' }} {{ $user->last_name ?? '' }}</div>
                <div class="user-email">{{ $user->email }}</div>
            </div>
        </div>
    @endif

    @if(session('error'))
        <div class="error-msg">{{ session('error') }}</div>
    @endif

    @php
        $durations = array_keys($grouped);
        $durationLabels = ['month' => 'Mensual', 'year' => 'Anual'];
    @endphp

    @if(count($durations) > 1)
        <div class="duration-tabs">
            @foreach($durations as $i => $dur)
                <button class="duration-tab {{ $i === 0 ? 'active' : '' }}" onclick="showDuration('{{ $dur }}', this)">
                    {{ $durationLabels[$dur] ?? ucfirst($dur) }}
                </button>
            @endforeach
        </div>
    @endif

    @foreach($grouped as $duration => $dPlans)
        <div class="plans-grid" id="plans-{{ $duration }}" @if($loop->first) style="display:flex" @endif>
            @foreach($dPlans as $plan)
                <div class="plan-card {{ $plan->name === 'Premium Plan' ? 'popular' : '' }}">
                    <div class="plan-name">{{ $plan->name }}</div>
                    <div class="plan-price">
                        ${{ number_format($plan->price, 2) }}
                        <span>/ {{ $durationLabels[$plan->duration] ?? $plan->duration }}</span>
                    </div>
                    <div class="plan-duration">
                        {{ $plan->duration_value ?? 1 }} {{ $plan->duration === 'year' ? 'ano(s)' : 'mes(es)' }}
                    </div>
                    <ul class="plan-features">
                        <li>Reproduccion sin limites</li>
                        <li>Calidad de audio premium</li>
                        @if($plan->name !== 'Basic Plan')
                            <li>Descarga para escuchar offline</li>
                            <li>Sin anuncios</li>
                        @endif
                    </ul>
                    @if($user)
                        <a href="{{ url('/app-music/pricing/checkout/' . $plan->id) }}?user_id={{ $user->id }}" class="plan-btn">
                            Elegir Plan
                        </a>
                    @else
                        <button class="plan-btn" onclick="showLogin({{ $plan->id }})">Elegir Plan</button>
                    @endif
                </div>
            @endforeach
        </div>
    @endforeach

    <div class="footer">Yammbo Music &copy; {{ date('Y') }}</div>

    <!-- Login overlay (shown if user is not authenticated) -->
    <div class="login-overlay" id="loginOverlay">
        <div class="login-box">
            <h2>Inicia sesion para continuar</h2>
            <form method="POST" action="{{ url('/app-music/pricing/login') }}">
                @csrf
                <input type="hidden" name="plan_id" id="selectedPlanId" value="">

                <div class="form-group">
                    <label>Correo electronico</label>
                    <input type="email" name="email" required placeholder="tu@email.com" autocomplete="email">
                </div>
                <div class="form-group">
                    <label>Contrasena</label>
                    <input type="password" name="password" required autocomplete="current-password">
                </div>
                <button type="submit" class="btn-login">Iniciar Sesion</button>
            </form>
            <button class="btn-cancel" onclick="hideLogin()">Cancelar</button>
        </div>
    </div>

    <script>
        function showDuration(dur, btn) {
            document.querySelectorAll('.plans-grid').forEach(function(g) { g.style.display = 'none'; });
            document.querySelectorAll('.duration-tab').forEach(function(t) { t.classList.remove('active'); });
            document.getElementById('plans-' + dur).style.display = 'flex';
            btn.classList.add('active');
        }
        function showLogin(planId) {
            document.getElementById('selectedPlanId').value = planId;
            document.getElementById('loginOverlay').classList.add('active');
        }
        function hideLogin() {
            document.getElementById('loginOverlay').classList.remove('active');
        }
    </script>
</body>
</html>
