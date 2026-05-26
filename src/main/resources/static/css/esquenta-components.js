// ═══════════════════════════════════════════════════
// ESQUENTA.IO — Shared JS Components (vanilla)
// ═══════════════════════════════════════════════════

// ── Mascot SVG ──
function esqMascot(size = 120, mood = 'happy') {
  const w = size, h = Math.round(size * 1.3);
  const faces = {
    happy:  `<circle cx="40" cy="45" r="2.5" fill="#15110F"/><circle cx="60" cy="45" r="2.5" fill="#15110F"/><path d="M42 50 Q50 55 58 50" stroke="#15110F" stroke-width="2.5" fill="none" stroke-linecap="round"/>`,
    wink:   `<path d="M37 45 L43 45" stroke="#15110F" stroke-width="2.5" stroke-linecap="round"/><circle cx="60" cy="45" r="2.5" fill="#15110F"/><path d="M42 50 Q50 56 58 51" stroke="#15110F" stroke-width="2.5" fill="none" stroke-linecap="round"/>`,
    tipsy:  `<path d="M37 45 Q40 42 43 45" stroke="#15110F" stroke-width="2.5" fill="none" stroke-linecap="round"/><path d="M57 45 Q60 42 63 45" stroke="#15110F" stroke-width="2.5" fill="none" stroke-linecap="round"/><ellipse cx="50" cy="51" rx="4" ry="2" fill="#15110F"/><circle cx="32" cy="50" r="3" fill="#FF4757" opacity="0.5"/><circle cx="68" cy="50" r="3" fill="#FF4757" opacity="0.5"/>`,
    shock:  `<circle cx="40" cy="44" r="3" fill="#15110F"/><circle cx="60" cy="44" r="3" fill="#15110F"/><ellipse cx="50" cy="52" rx="3" ry="4" fill="#15110F"/>`,
    smirk:  `<circle cx="40" cy="45" r="2.5" fill="#15110F"/><circle cx="60" cy="45" r="2.5" fill="#15110F"/><path d="M42 52 Q50 50 58 53" stroke="#15110F" stroke-width="2.5" fill="none" stroke-linecap="round"/>`,
  };
  return `<svg viewBox="0 0 100 130" width="${w}" height="${h}">
    <ellipse cx="50" cy="124" rx="32" ry="4" fill="rgba(26,22,20,0.2)"/>
    <rect x="34" y="6" width="32" height="14" rx="3" fill="#FF4757" stroke="#15110F" stroke-width="3"/>
    <rect x="40" y="18" width="20" height="10" fill="#FFFBF2" stroke="#15110F" stroke-width="3"/>
    <path d="M28 38 Q28 28 50 28 Q72 28 72 38 L72 110 Q72 120 60 120 L40 120 Q28 120 28 110 Z" fill="#FFB800" stroke="#15110F" stroke-width="3"/>
    <rect x="32" y="55" width="36" height="32" fill="#FFFBF2" stroke="#15110F" stroke-width="2.5"/>
    <text x="50" y="71" font-family="'Bagel Fat One', sans-serif" font-size="11" text-anchor="middle" fill="#15110F">E</text>
    <line x1="36" y1="78" x2="64" y2="78" stroke="#15110F" stroke-width="1.5" opacity="0.4"/>
    ${faces[mood] || faces.happy}
    <circle cx="42" cy="98" r="3" fill="#FFFBF2" stroke="#15110F" stroke-width="1.5"/>
    <circle cx="56" cy="103" r="2" fill="#FFFBF2" stroke="#15110F" stroke-width="1.5"/>
    <circle cx="48" cy="108" r="2.5" fill="#FFFBF2" stroke="#15110F" stroke-width="1.5"/>
  </svg>`;
}

// ── Starburst SVG ──
function esqStarburst(size = 80, color = '#FFB800', content = '') {
  const pts = Array.from({length:16},(_,i)=>{const a=(i/16)*Math.PI*2,r=i%2===0?48:32;return `${Math.cos(a)*r},${Math.sin(a)*r}`;}).join(' ');
  return `<div style="position:relative;width:${size}px;height:${size}px;display:flex;align-items:center;justify-content:center;">
    <svg viewBox="-50 -50 100 100" width="${size}" height="${size}" style="position:absolute;inset:0;animation:esq-spin-slow 20s linear infinite">
      <polygon points="${pts}" fill="${color}" stroke="#15110F" stroke-width="3" stroke-linejoin="round"/>
    </svg>
    <div style="position:relative;font-family:'Archivo Black',sans-serif;font-size:${size*0.12}px;text-align:center;color:#15110F;text-transform:uppercase;line-height:1;padding:4px;">${content}</div>
  </div>`;
}

// ── Avatar colors & shapes ──
const ESQ_AVATAR_COLORS  = ['#FFB800','#FF4757','#2DD4BF','#6B2C8A','#FF8C42','#3D8BFD'];
const ESQ_AVATAR_SHAPES  = ['circle','square','diamond','flower','star','shield'];
function esqAvatar(name, idx = 0, size = 56, isHost = false, isTurn = false) {
  const color = ESQ_AVATAR_COLORS[idx % ESQ_AVATAR_COLORS.length];
  const shape = ESQ_AVATAR_SHAPES[idx % ESQ_AVATAR_SHAPES.length];
  const init  = (name||'?').slice(0,2).toUpperCase();
  const shapeMap = {
    circle:  `<circle cx="0" cy="0" r="44" fill="${color}" stroke="#15110F" stroke-width="5"/>`,
    square:  `<rect x="-42" y="-42" width="84" height="84" rx="10" fill="${color}" stroke="#15110F" stroke-width="5"/>`,
    diamond: `<polygon points="0,-46 46,0 0,46 -46,0" fill="${color}" stroke="#15110F" stroke-width="5" stroke-linejoin="round"/>`,
    flower:  [0,1,2,3,4,5].map(i=>`<circle cx="${Math.cos(i*Math.PI/3)*22}" cy="${Math.sin(i*Math.PI/3)*22}" r="22" fill="${color}" stroke="#15110F" stroke-width="5"/>`).join('')+`<circle cx="0" cy="0" r="22" fill="${color}" stroke="#15110F" stroke-width="5"/>`,
    star:    `<polygon points="${Array.from({length:10},(_,i)=>{const a=(i/10)*Math.PI*2-Math.PI/2,r=i%2===0?46:22;return `${Math.cos(a)*r},${Math.sin(a)*r}`;}).join(' ')}" fill="${color}" stroke="#15110F" stroke-width="5" stroke-linejoin="round"/>`,
    shield:  `<path d="M0 -46 L42 -32 L42 12 Q42 38 0 46 Q-42 38 -42 12 L-42 -32 Z" fill="${color}" stroke="#15110F" stroke-width="5" stroke-linejoin="round"/>`,
  };
  const pulseRing = isTurn ? `<div style="position:absolute;inset:-6px;border:4px solid #FF4757;border-radius:50%;animation:esq-pulse-ring 1.4s ease-out infinite;pointer-events:none;"></div><div style="position:absolute;inset:-6px;border:4px solid #FF4757;border-radius:50%;animation:esq-pulse-ring 1.4s 0.7s ease-out infinite;pointer-events:none;"></div>` : '';
  const crownBadge = isHost ? `<div style="position:absolute;top:-8px;right:-8px;width:${size*.42}px;height:${size*.42}px;background:#FFB800;border:3px solid #15110F;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:${size*.22}px;">👑</div>` : '';
  return `<div style="position:relative;width:${size}px;height:${size}px;flex-shrink:0;">
    <svg viewBox="-50 -50 100 100" width="${size}" height="${size}">${shapeMap[shape]||shapeMap.circle}</svg>
    <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-family:'Bagel Fat One',sans-serif;font-size:${size*0.34}px;color:#15110F;pointer-events:none;">${init}</div>
    ${crownBadge}${pulseRing}
  </div>`;
}

// ── Marquee init ──
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-mascot]').forEach(el => {
    el.innerHTML = esqMascot(parseInt(el.dataset.size||120), el.dataset.mood||'happy');
  });
  document.querySelectorAll('[data-avatar]').forEach(el => {
    el.innerHTML = esqAvatar(el.dataset.name, parseInt(el.dataset.idx||0), parseInt(el.dataset.size||56), el.dataset.host==='true', el.dataset.turn==='true');
  });

  // PIN input auto-advance
  document.querySelectorAll('.esq-pin-slot').forEach((slot, i, slots) => {
    slot.addEventListener('input', e => {
      e.target.value = e.target.value.replace(/[^0-9]/g,'').slice(-1);
      if (e.target.value && slots[i+1]) slots[i+1].focus();
    });
    slot.addEventListener('keydown', e => {
      if (e.key === 'Backspace' && !e.target.value && slots[i-1]) slots[i-1].focus();
    });
  });

  // Confetti
  document.querySelectorAll('[data-confetti]').forEach(el => {
    const colors = ['#FFB800','#FF4757','#2DD4BF','#6B2C8A','#FFFBF2'];
    const count = parseInt(el.dataset.confetti||30);
    el.innerHTML = Array.from({length:count},(_,i)=>{
      const cx=(Math.random()-.5)*600, cy=-200-Math.random()*200;
      const l=30+Math.random()*40, t=50+Math.random()*20;
      const shape=Math.random()>.5?'50%':'2px', sz=8+Math.random()*8;
      return `<div style="position:absolute;left:${l}%;top:${t}%;width:${sz}px;height:${sz}px;background:${colors[i%colors.length]};border:2px solid #15110F;border-radius:${shape};--cx:${cx}px;--cy:${cy}px;animation:esq-confetti 1.2s ${Math.random()*.4}s ease-out forwards;"></div>`;
    }).join('');
  });

  // Timer
  const timerEl = document.getElementById('esq-timer-bar');
  const timerNum = document.getElementById('esq-timer-num');
  if (timerEl && timerNum) {
    let left = parseInt(timerEl.dataset.seconds||30);
    const total = left;
    function tick() {
      if (left <= 0) return;
      left--;
      const pct = (left/total)*100;
      const color = left<=5?'#FF4757':left<=10?'#FFB800':'#2DD4BF';
      timerEl.style.width = pct+'%';
      timerEl.style.background = color;
      timerNum.textContent = left;
      timerNum.parentElement.style.background = color;
      if (left<=5) timerNum.parentElement.style.animation='esq-bounce 0.5s ease-in-out infinite';
      if (left>0) {
        setTimeout(tick, 1000);
      } else {
        // Timer expirou — dispara callback global se definido
        if (typeof window.onTimerExpired === 'function') window.onTimerExpired();
      }
    }
    setTimeout(tick, 1000);
  }
});
