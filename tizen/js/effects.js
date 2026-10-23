/**
 * Rerun Retro Effects — StaticNoise, NoiseBurst, CrtOverlay, VerticalRoll, PowerTransition
 */
(function (global) {
    'use strict';

    const NoiseBurst = {
        _ctx: null,
        _buffer: null,
        play() {
            try {
                const AudioCtx = window.AudioContext || window.webkitAudioContext;
                if (!AudioCtx) return;
                if (!this._ctx) this._ctx = new AudioCtx();
                if (this._ctx.state === 'suspended') this._ctx.resume();

                const rate = 22050;
                const n = Math.floor(rate * 0.15);
                if (!this._buffer) {
                    this._buffer = this._ctx.createBuffer(1, n, rate);
                    const data = this._buffer.getChannelData(0);
                    for (let i = 0; i < n; i++) {
                        const fade = i > n - 600 ? (n - i) / 600 : 1;
                        data[i] = ((Math.random() * 2 - 1) * 0.18) * fade;
                    }
                }
                const src = this._ctx.createBufferSource();
                src.buffer = this._buffer;
                src.connect(this._ctx.destination);
                src.start();
            } catch (_) { }
        }
    };

    class StaticNoiseRenderer {
        constructor(canvas) {
            this.canvas = canvas;
            this.ctx = canvas.getContext('2d');
            this.off = document.createElement('canvas');
            this.off.width = 160;
            this.off.height = 90;
            this.offCtx = this.off.getContext('2d');
            this.imgData = this.offCtx.createImageData(160, 90);
            this.u32 = new Uint32Array(this.imgData.data.buffer);
            this.active = false;
            this._raf = null;
        }

        setActive(on) {
            if (this.active === on) return;
            this.active = on;
            this.canvas.style.display = on ? 'block' : 'none';
            if (on) this._loop();
            else if (this._raf) {
                cancelAnimationFrame(this._raf);
                this._raf = null;
            }
        }

        _loop() {
            if (!this.active) return;
            const buf = this.u32;
            for (let i = 0; i < buf.length; i++) {
                const v = (Math.random() * 256) | 0;
                buf[i] = (255 << 24) | (v << 16) | (v << 8) | v;
            }
            this.offCtx.putImageData(this.imgData, 0, 0);
            this.ctx.imageSmoothingEnabled = false;
            this.ctx.drawImage(this.off, 0, 0, 1920, 1080);
            this._raf = requestAnimationFrame(() => this._loop());
        }
    }

    const CrtOverlay = {
        draw(canvas, level) {
            const ctx = canvas.getContext('2d');
            ctx.clearRect(0, 0, 1920, 1080);
            document.body.classList.toggle('crt-full', level === 'FULL');
            if (level === 'OFF') return;

            const spec = level === 'FULL'
                ? { scanAlpha: 0.28, vignetteEdge: 0.55, cornerPx: 48, insetFraction: 0.015, glow: 0.04 }
                : { scanAlpha: 0.12, vignetteEdge: 0.25, cornerPx: 24, insetFraction: 0, glow: 0 };

            // 1px dark line every 3px
            ctx.fillStyle = `rgba(0, 0, 0, ${spec.scanAlpha})`;
            for (let y = 2; y < 1080; y += 3) {
                ctx.fillRect(0, y, 1920, 1);
            }

            // Radial vignette
            const r = Math.max(1920, 1080) * 0.75;
            const vig = ctx.createRadialGradient(960, 540, r * 0.55, 960, 540, r);
            vig.addColorStop(0, 'rgba(0,0,0,0)');
            vig.addColorStop(1, `rgba(0,0,0,${spec.vignetteEdge})`);
            ctx.fillStyle = vig;
            ctx.fillRect(0, 0, 1920, 1080);

            // Soft screen glow
            if (spec.glow > 0) {
                const gl = ctx.createRadialGradient(960, 540, 0, 960, 540, r * 0.7);
                gl.addColorStop(0, `rgba(255,255,255,${spec.glow})`);
                gl.addColorStop(1, 'rgba(255,255,255,0)');
                ctx.fillStyle = gl;
                ctx.fillRect(0, 0, 1920, 1080);
            }

            // Rounded tube mask
            const inset = 1080 * spec.insetFraction;
            const rad = spec.cornerPx;
            ctx.fillStyle = '#000';
            ctx.beginPath();
            ctx.rect(0, 0, 1920, 1080);
            this._roundRect(ctx, inset, inset, 1920 - inset * 2, 1080 - inset * 2, rad);
            ctx.fill('evenodd');
        },
        _roundRect(ctx, x, y, w, h, r) {
            ctx.moveTo(x + r, y);
            ctx.lineTo(x + w - r, y);
            ctx.quadraticCurveTo(x + w, y, x + w, y + r);
            ctx.lineTo(x + w, y + h - r);
            ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
            ctx.lineTo(x + r, y + h);
            ctx.quadraticCurveTo(x, y + h, x, y + h - r);
            ctx.lineTo(x, y + r);
            ctx.quadraticCurveTo(x, y, x + r, y);
            ctx.closePath();
        }
    };

    const VerticalRoll = {
        play(canvas) {
            const ctx = canvas.getContext('2d');
            const start = performance.now();
            const dur = 250;
            const bandH = 1080 * 0.15;

            const step = (now) => {
                const p = Math.min(1, (now - start) / dur);
                ctx.clearRect(0, 0, 1920, 1080);
                if (p < 1) {
                    const y = -bandH + (1080 + bandH) * p;
                    ctx.fillStyle = 'rgba(0, 0, 0, 0.85)';
                    ctx.fillRect(0, y, 1920, bandH);
                    ctx.fillStyle = 'rgba(255, 255, 255, 0.18)';
                    ctx.fillRect(0, y + bandH, 1920, 3);
                    requestAnimationFrame(step);
                }
            };
            requestAnimationFrame(step);
        }
    };

    const PowerTransition = {
        powerOn(canvas) {
            const ctx = canvas.getContext('2d');
            const start = performance.now();
            const dur = 350;
            const step = (now) => {
                const p = Math.min(1, (now - start) / dur);
                ctx.clearRect(0, 0, 1920, 1080);
                if (p < 1) {
                    const ease = 1 - Math.pow(1 - p, 3);
                    const bandH = Math.max(2, 1080 * ease);
                    const top = (1080 - bandH) / 2;
                    ctx.fillStyle = '#000';
                    ctx.fillRect(0, 0, 1920, top);
                    ctx.fillRect(0, top + bandH, 1920, 1080 - top - bandH);
                    ctx.fillStyle = `rgba(255, 255, 255, ${(1 - ease) * 0.85})`;
                    ctx.fillRect(0, top, 1920, bandH);
                    requestAnimationFrame(step);
                }
            };
            requestAnimationFrame(step);
        },
        powerOff(canvas, onDone) {
            const ctx = canvas.getContext('2d');
            const start = performance.now();
            const dur = 300;
            const step = (now) => {
                const q = Math.min(1, (now - start) / dur);
                ctx.clearRect(0, 0, 1920, 1080);
                const v = Math.min(1, q / 0.7);
                const lineH = Math.max(2, 1080 * (1 - v));
                const horiz = Math.max(0, Math.min(1, (q - 0.7) / 0.3));
                const lineW = Math.max(4, 1920 * (1 - horiz));
                ctx.fillStyle = '#000';
                ctx.fillRect(0, 0, 1920, 1080);
                ctx.fillStyle = `rgba(255, 255, 255, ${0.35 + 0.65 * v})`;
                ctx.fillRect((1920 - lineW) / 2, (1080 - lineH) / 2, lineW, lineH);
                if (q < 1) requestAnimationFrame(step);
                else if (onDone) onDone();
            };
            requestAnimationFrame(step);
        }
    };

    global.RerunEffects = {
        NoiseBurst,
        StaticNoiseRenderer,
        CrtOverlay,
        VerticalRoll,
        PowerTransition,
    };
})(window);
