export class StatsOverlay {
  private fpsEl: HTMLElement;
  private resEl: HTMLElement;

  constructor(fpsEl: HTMLElement, resEl: HTMLElement) {
    this.fpsEl = fpsEl;
    this.resEl = resEl;
  }

  updateFPS(frameTimeMs: number) {
    const fps = (1000 / frameTimeMs).toFixed(1);
    this.fpsEl.textContent = fps;
  }

  updateResolution(width: number, height: number) {
    this.resEl.textContent = `${width}x${height}`;
  }
}
