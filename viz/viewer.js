
const PX = 1.8 / 32; // 1 pixel Minecraft, pour un joueur de 1.8 bloc

function boot(modelId) {
  const MODELS = window.DLB_MODELS, H = window.DLB_H;
  const def = MODELS[modelId];
  const root = document.getElementById('scene');

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x0e1117);

  const camera = new THREE.PerspectiveCamera(50, 1, 0.01, 100);
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  root.appendChild(renderer.domElement);

  // ---- sol et reperes ------------------------------------------------------
  const grid = new THREE.GridHelper(8, 16, 0x2a3140, 0x1b2130);
  scene.add(grid);

  // regle verticale : un trait tous les 0.5 bloc, pour lire les hauteurs
  const ruler = new THREE.Group();
  for (let i = 0; i <= 5; i++) {
    const y = i * 0.5;
    const g = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-1.5, y, -1.5), new THREE.Vector3(-1.35, y, -1.5)]);
    ruler.add(new THREE.Line(g, new THREE.LineBasicMaterial({ color: 0x3d4760 })));
  }
  scene.add(ruler);

  // ---- squelette du joueur (proportions Minecraft, 1.8 bloc) ---------------
  const skel = new THREE.Group();
  function bone(w, h, d, x, y, z, label) {
    const geo = new THREE.BoxGeometry(w, h, d);
    const mesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({
      color: 0x8aa0c0, transparent: true, opacity: 0.13, depthWrite: false }));
    mesh.position.set(x, y + h / 2, z);
    skel.add(mesh);
    const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geo),
      new THREE.LineBasicMaterial({ color: 0x6f86a8, transparent: true, opacity: 0.85 }));
    edges.position.copy(mesh.position);
    skel.add(edges);
    mesh.userData.label = label;
  }
  bone(4 * PX, 12 * PX, 4 * PX, -2 * PX, 0, 0, 'jambe G');
  bone(4 * PX, 12 * PX, 4 * PX,  2 * PX, 0, 0, 'jambe D');
  bone(8 * PX, 12 * PX, 4 * PX, 0, 12 * PX, 0, 'torse');
  bone(4 * PX, 12 * PX, 4 * PX, -6 * PX, 12 * PX, 0, 'bras G');
  bone(4 * PX, 12 * PX, 4 * PX,  6 * PX, 12 * PX, 0, 'bras D');
  bone(8 * PX, 8 * PX, 8 * PX, 0, 24 * PX, 0, 'tete');
  scene.add(skel);

  // repere du regard : le joueur fait face a -Z (comme fwd dans le code)
  const nose = new THREE.Mesh(new THREE.ConeGeometry(0.05, 0.14, 4),
    new THREE.MeshBasicMaterial({ color: 0x38bdf8 }));
  nose.position.set(0, 28 * PX, -0.30);
  nose.rotation.x = -Math.PI / 2;
  scene.add(nose);

  // ---- pool de cubes de l'ornement ----------------------------------------
  const POOL = 400;
  const geo = new THREE.BoxGeometry(1, 1, 1);
  const cubes = [];
  for (let i = 0; i < POOL; i++) {
    const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }));
    m.visible = false;
    scene.add(m);
    cubes.push(m);
  }

  // ---- camera orbitale (ecrite a la main : aucune dependance en plus) ------
  let yaw = 0.7, pitch = 0.22, dist = 4.2, target = new THREE.Vector3(0, 1.1, 0);
  let drag = false, lx = 0, ly = 0;
  const cv = renderer.domElement;
  cv.addEventListener('pointerdown', e => { drag = true; lx = e.clientX; ly = e.clientY; cv.setPointerCapture(e.pointerId); });
  cv.addEventListener('pointerup', e => { drag = false; });
  cv.addEventListener('pointermove', e => {
    if (!drag) return;
    yaw -= (e.clientX - lx) * 0.008;
    pitch = Math.max(-1.3, Math.min(1.45, pitch + (e.clientY - ly) * 0.006));
    lx = e.clientX; ly = e.clientY;
  });
  cv.addEventListener('wheel', e => {
    e.preventDefault();
    dist = Math.max(1.2, Math.min(12, dist * (1 + Math.sign(e.deltaY) * 0.12)));
  }, { passive: false });

  // ---- commandes -----------------------------------------------------------
  let paused = false, speed = 1, showSkel = true, t = 0;
  document.getElementById('pause').onclick = e => {
    paused = !paused; e.target.textContent = paused ? 'Reprendre' : 'Pause';
  };
  document.getElementById('slow').onclick = e => {
    speed = speed === 1 ? 0.25 : 1; e.target.textContent = speed === 1 ? 'Ralenti' : 'Vitesse normale';
  };
  document.getElementById('skel').onclick = e => {
    showSkel = !showSkel; skel.visible = showSkel; nose.visible = showSkel;
    e.target.textContent = showSkel ? 'Masquer le squelette' : 'Afficher le squelette';
  };

  const compteur = document.getElementById('compteur');

  function resize() {
    const w = root.clientWidth, h = root.clientHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }
  addEventListener('resize', resize);
  resize();

  let last = performance.now();
  function frame(now) {
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    if (!paused) t += dt * speed;

    const liste = def.build(t);
    for (let i = 0; i < POOL; i++) {
      const c = cubes[i];
      if (i >= liste.length) { c.visible = false; continue; }
      const v = liste[i];
      c.visible = true;
      c.position.set(v.x, v.y, v.z);
      c.scale.set(v.hx * 2, v.hy * 2, v.hz * 2); // demi-extensions -> taille
      c.material.color.setHex(v.rgb);
      c.material.opacity = v.a;
    }
    if (compteur) compteur.textContent = liste.length + ' cubes';

    camera.position.set(
      target.x + Math.cos(pitch) * Math.sin(yaw) * dist,
      target.y + Math.sin(pitch) * dist,
      target.z + Math.cos(pitch) * Math.cos(yaw) * dist);
    camera.lookAt(target);
    renderer.render(scene, camera);
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
}
