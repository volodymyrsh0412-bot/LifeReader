// Tajmer i sekundomir. Vbudovanyi skrypt Niky (sproshchenyj analoh skryptu shpak dlia SandFly).
export const manifest = {
  id: 'timer-stopwatch',
  name: 'Таймер і секундомір',
  version: '1.0',
  author: 'shpak (SandFly), Nika',
  description: 'Таймер зворотного відліку і секундомір. Кожну дію можна призначити на жест або клавішу на сторінці скрипта. Таймер звучить, поки екран увімкнений; якщо екран був вимкнений, про завершення повідомить при першому ввімкненні.',
  permissions: ['speech', 'input'],
  commands: [
    { id: 'timer', title: 'Таймер: запустити або дізнатися час', menu: true },
    { id: 'timer_pause', title: 'Таймер: пауза або продовження', menu: true },
    { id: 'timer_stop', title: 'Таймер: зупинити', menu: true },
    { id: 'stopwatch', title: 'Секундомір: запустити або дізнатися час', menu: true },
    { id: 'stopwatch_pause', title: 'Секундомір: пауза або продовження', menu: true },
    { id: 'stopwatch_stop', title: 'Секундомір: зупинити', menu: true },
  ],
  settings: [
    { key: 'minutes', type: 'number', title: 'Таймер за замовчуванням, хвилин', summary: 'На стільки хвилин запускається таймер одним жестом', default: 5 },
    { key: 'repeat', type: 'number', title: 'Скільки разів повторити сигнал про завершення', default: 3 },
  ],
};

function plural(n, one, few, many) {
  const a = Math.abs(n) % 100;
  const b = a % 10;
  if (a > 10 && a < 20) return many;
  if (b > 1 && b < 5) return few;
  if (b === 1) return one;
  return many;
}

function words(ms) {
  let total = Math.max(0, Math.round(ms / 1000));
  const h = Math.floor(total / 3600);
  total -= h * 3600;
  const m = Math.floor(total / 60);
  const s = total - m * 60;
  const out = [];
  if (h > 0) out.push(h + ' ' + plural(h, 'година', 'години', 'годин'));
  if (m > 0) out.push(m + ' ' + plural(m, 'хвилина', 'хвилини', 'хвилин'));
  if (s > 0 || out.length === 0) out.push(s + ' ' + plural(s, 'секунда', 'секунди', 'секунд'));
  return out.join(' ');
}

function load(key) {
  try {
    return backtalk.storage.get(key, null);
  } catch (e) {
    return null;
  }
}

function save(key, value) {
  try {
    if (value === null) backtalk.storage.remove(key);
    else backtalk.storage.set(key, value);
  } catch (e) {
    console.error('timer: ' + e);
  }
}

function say(text) {
  backtalk.speak(text, { interrupt: true });
}

// ---- Tajmer. Stan: { end } (pracuje) abo { left } (pauza), plus { total }.
function timerLeft(t) {
  if (!t) return 0;
  return t.left !== undefined ? t.left : Math.max(0, t.end - Date.now());
}

function startTimer() {
  const setting = Number(backtalk.settings.get('minutes'));
  const minutes = setting > 0 ? setting : 5;
  const ms = Math.round(minutes * 60000);
  save('timer', { end: Date.now() + ms, total: ms });
  say('Таймер: ' + words(ms));
}

function ring(text) {
  const times = Math.max(1, Math.min(10, Math.round(Number(backtalk.settings.get('repeat')) || 3)));
  let n = 0;
  const once = () => {
    backtalk.playTone(880, 150, { volume: 1 });
    backtalk.vibrate([0, 200, 100, 200]);
    say(text);
    n += 1;
    if (n < times) setTimeout(once, 3000);
  };
  once();
}

function check() {
  const t = load('timer');
  if (t && t.end !== undefined && Date.now() >= t.end) {
    save('timer', null);
    ring('Таймер завершено');
  }
}

backtalk.onCommand('timer', () => {
  const t = load('timer');
  if (!t) {
    startTimer();
    return;
  }
  say((t.left !== undefined ? 'Пауза. ' : '') + 'Залишилось ' + words(timerLeft(t)));
});

backtalk.onCommand('timer_pause', () => {
  const t = load('timer');
  if (!t) {
    say('Таймер не запущено');
    return;
  }
  if (t.left !== undefined) {
    save('timer', { end: Date.now() + t.left, total: t.total });
    say('Продовжую. Залишилось ' + words(t.left));
  } else {
    const left = Math.max(0, t.end - Date.now());
    save('timer', { left: left, total: t.total });
    say('Пауза. Залишилось ' + words(left));
  }
});

backtalk.onCommand('timer_stop', () => {
  if (!load('timer')) {
    say('Таймер не запущено');
    return;
  }
  save('timer', null);
  say('Таймер зупинено');
});

// ---- Sekundomir. Stan: { start } (pracuje) abo { elapsed } (pauza).
function elapsedOf(s) {
  if (!s) return 0;
  return s.elapsed !== undefined ? s.elapsed : Date.now() - s.start;
}

backtalk.onCommand('stopwatch', () => {
  const s = load('stopwatch');
  if (!s) {
    save('stopwatch', { start: Date.now() });
    say('Секундомір запущено');
    return;
  }
  say((s.elapsed !== undefined ? 'Пауза. ' : '') + words(elapsedOf(s)));
});

backtalk.onCommand('stopwatch_pause', () => {
  const s = load('stopwatch');
  if (!s) {
    say('Секундомір не запущено');
    return;
  }
  if (s.elapsed !== undefined) {
    save('stopwatch', { start: Date.now() - s.elapsed });
    say('Продовжую');
  } else {
    const e = Date.now() - s.start;
    save('stopwatch', { elapsed: e });
    say('Пауза. ' + words(e));
  }
});

backtalk.onCommand('stopwatch_stop', () => {
  const s = load('stopwatch');
  if (!s) {
    say('Секундомір не запущено');
    return;
  }
  save('stopwatch', null);
  say('Секундомір зупинено. ' + words(elapsedOf(s)));
});

setInterval(check, 1000);
check();
