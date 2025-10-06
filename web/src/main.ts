import { StatsOverlay } from "./utils/stats";

const frameEl = document.getElementById("frame") as HTMLImageElement;
const fpsEl = document.getElementById("fps") as HTMLElement;
const resolutionEl = document.getElementById("resolution") as HTMLElement;

const frames: string[] = [];

for (let i = 1; i <= 10; i++) {
  const padded = ("000" + i).slice(-3); 
  frames.push(`frames/frame_${padded}.jpg`);
}

let index = 0;
const overlay = new StatsOverlay(fpsEl, resolutionEl);

function nextFrame() {
  const start = performance.now();
  frameEl.src = frames[index];
  index = (index + 1) % frames.length;

  
  frameEl.onload = () => {
    overlay.updateResolution(frameEl.naturalWidth, frameEl.naturalHeight);
    overlay.updateFPS(performance.now() - start);
  };
}

setInterval(nextFrame, 100); 
