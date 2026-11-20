// =============================================================================
//  Visualiseur unique : selection A GAUCHE, apercu A DROITE.
//  Scripts classiques (pas de modules ES) : la page s'ouvre aussi en file://,
//  par simple double-clic, sans serveur.
// =============================================================================

const PX = 1.8 / 32;          // 1 pixel Minecraft pour un joueur de 1.8 bloc

(function () {
  const MODELS = window.DLB_MODELS;
  const ZR = window.DLB_ZONE_REGARD;

  // ---- scene --------------------------------------------------------------
  const hote = document.getElementById('scene');
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x0d1117);
  const camera = new THREE.PerspectiveCamera(48, 1, 0.01, 100);
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  hote.appendChild(renderer.domElement);

  scene.add(new THREE.GridHelper(8, 32, 0x273041, 0x1a2130));

  // ---- squelette du joueur (proportions Minecraft) -------------------------
  const squelette = new THREE.Group();
  function os(w, h, d, x, y, z) {
    const g = new THREE.BoxGeometry(w, h, d);
    const m = new THREE.Mesh(g, new THREE.MeshBasicMaterial({
      color: 0x7e93b4, transparent: true, opacity: 0.10, depthWrite: false }));
    m.position.set(x, y + h / 2, z);
    squelette.add(m);
    const e = new THREE.LineSegments(new THREE.EdgesGeometry(g),
      new THREE.LineBasicMaterial({ color: 0x6b82a6, transparent: true, opacity: 0.9 }));
    e.position.copy(m.position);
    squelette.add(e);
  }
  os(4 * PX, 12 * PX, 4 * PX, -2 * PX, 0, 0);          // jambe gauche
  os(4 * PX, 12 * PX, 4 * PX, 2 * PX, 0, 0);           // jambe droite
  os(8 * PX, 12 * PX, 4 * PX, 0, 12 * PX, 0);          // torse
  os(4 * PX, 12 * PX, 4 * PX, -6 * PX, 12 * PX, 0);    // bras gauche
  os(4 * PX, 12 * PX, 4 * PX, 6 * PX, 12 * PX, 0);     // bras droit
  os(8 * PX, 8 * PX, 8 * PX, 0, 24 * PX, 0);           // tete
  scene.add(squelette);

  // Le regard : le joueur fait face a +Z.
  const nez = new THREE.Mesh(new THREE.ConeGeometry(0.045, 0.13, 4),
    new THREE.MeshBasicMaterial({ color: 0x38bdf8 }));
  nez.position.set(0, 1.62, 0.30);
  nez.rotation.x = Math.PI / 2;
  squelette.add(nez);

  // ---- zone du regard : le volume qu'aucun effet constant ne doit occuper ---
  const zoneGeo = new THREE.CylinderGeometry(ZR.rayon, ZR.rayon, ZR.haut - ZR.bas, 28, 1, true,
    -Math.PI / 2, Math.PI);
  const zone = new THREE.Mesh(zoneGeo, new THREE.MeshBasicMaterial({
    color: 0xef4444, transparent: true, opacity: 0.10, side: THREE.DoubleSide, depthWrite: false }));
  zone.position.set(0, (ZR.bas + ZR.haut) / 2, 0);
  zone.visible = false;
  scene.add(zone);

  // ---- cubes de l'ornement -------------------------------------------------
  const POOL = 1500;   // l'aile de l'Archange culmine a 1276 cubes (T435)
  const geo = new THREE.BoxGeometry(1, 1, 1);
  const cubes = [];
  for (let i = 0; i < POOL; i++) {
    const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }));
    m.visible = false;
    scene.add(m);
    cubes.push(m);
  }

  // ---- camera orbitale -----------------------------------------------------
  let lacet = 0.65, tangage = 0.20, dist = 3.6;
  const cible = new THREE.Vector3(0, 1.1, 0);
  let tire = false, lx = 0, ly = 0;
  const cv = renderer.domElement;
  cv.addEventListener('pointerdown', e => { tire = true; lx = e.clientX; ly = e.clientY; cv.setPointerCapture(e.pointerId); });
  cv.addEventListener('pointerup', () => { tire = false; });
  cv.addEventListener('pointermove', e => {
    if (!tire) return;
    lacet -= (e.clientX - lx) * 0.008;
    tangage = Math.max(-1.3, Math.min(1.45, tangage + (e.clientY - ly) * 0.006));
    lx = e.clientX; ly = e.clientY;
  });
  cv.addEventListener('wheel', e => {
    e.preventDefault();
    dist = Math.max(0.8, Math.min(12, dist * (1 + Math.sign(e.deltaY) * 0.12)));
  }, { passive: false });

  // ---- etat ---------------------------------------------------------------
  let courant = null, t = 0, pause = false, vitesse = 1;

  function cadrer(id) {
    // cadrage automatique : tete, sol ou corps entier selon l'ornement
    let bas = Infinity, haut = -Infinity, large = 0;
    for (let k = 0; k < 8; k++) {
      for (const c of MODELS[id].build(k * 0.4)) {
        bas = Math.min(bas, c.y); haut = Math.max(haut, c.y);
        large = Math.max(large, Math.hypot(c.x, c.z));
      }
    }
    const centreY = (bas + haut) / 2;
    // on garde toujours le joueur visible, mais on recentre vers l'ornement
    cible.set(0, Math.max(0.55, Math.min(1.75, (centreY + 0.95) / 2)), 0);
    dist = Math.max(2.4, Math.min(6.5, 2.0 + large * 2.3 + (haut - bas) * 0.8));
  }

  function choisir(id) {
    courant = id;
    t = 0;
    const d = MODELS[id];
    document.getElementById('titre').textContent = d.titre;
    const tag = document.getElementById('famille');
    tag.textContent = d.famille;
    tag.className = 'tag ' + (d.famille === 'permanent' ? 'perm' : 'ponc');
    document.getElementById('accorde').textContent = d.accorde;
    document.getElementById('lecture').textContent = d.note;
    for (const el of document.querySelectorAll('#liste a'))
      el.classList.toggle('actif', el.dataset.id === id);
    cadrer(id);
    // Une aile ne se juge pas de face : on se place d'office derriere le dos.
    if (id === 'archange_101' || id === 'winged_endurance' || id === 'wing_ribbons') {
      lacet = Math.PI; tangage = 0.14;
    }
    try { history.replaceState(null, '', '#' + id); } catch (e) { /* file:// */ }
  }
  window.choisir = choisir;

  // ---- commandes -----------------------------------------------------------
  document.getElementById('pause').onclick = e => {
    pause = !pause; e.target.textContent = pause ? 'Reprendre' : 'Pause';
  };
  document.getElementById('lent').onclick = e => {
    vitesse = vitesse === 1 ? 0.25 : 1;
    e.target.textContent = vitesse === 1 ? 'Ralenti' : 'Vitesse normale';
  };
  document.getElementById('sq').onclick = e => {
    squelette.visible = !squelette.visible;
    e.target.textContent = squelette.visible ? 'Masquer le joueur' : 'Afficher le joueur';
  };
  document.getElementById('zone').onclick = e => {
    zone.visible = !zone.visible;
    e.target.classList.toggle('on', zone.visible);
    e.target.textContent = zone.visible ? 'Masquer la zone du regard' : 'Zone du regard';
  };
  document.getElementById('vdos').onclick = () => { lacet = Math.PI; tangage = 0.12; };
  document.getElementById('vdessus').onclick = () => { lacet = 0.0; tangage = 1.32; };

  // ---- boucle --------------------------------------------------------------
  function redim() {
    const w = hote.clientWidth, h = hote.clientHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }
  addEventListener('resize', redim);
  redim();

  let dernier = performance.now();
  function image(now) {
    const dt = Math.min(0.05, (now - dernier) / 1000);
    dernier = now;
    if (!pause) t += dt * vitesse;

    const liste = courant ? MODELS[courant].build(t) : [];
    for (let i = 0; i < POOL; i++) {
      const c = cubes[i];
      if (i >= liste.length) { c.visible = false; continue; }
      const v = liste[i];
      c.visible = true;
      c.position.set(v.x, v.y, v.z);
      c.scale.set(v.hx * 2, v.hy * 2, v.hz * 2);
      c.material.color.setHex(v.rgb);
      c.material.opacity = v.a;
    }
    const cpt = document.getElementById('compteur');
    if (cpt) cpt.textContent = liste.length + (liste.length > POOL ? '+' : '') + ' cubes';

    camera.position.set(
      cible.x + Math.cos(tangage) * Math.sin(lacet) * dist,
      cible.y + Math.sin(tangage) * dist,
      cible.z + Math.cos(tangage) * Math.cos(lacet) * dist);
    camera.lookAt(cible);
    renderer.render(scene, camera);
    requestAnimationFrame(image);
  }
  requestAnimationFrame(image);

  // ---- liste de gauche -----------------------------------------------------
  const liste = document.getElementById('liste');
  function section(titre, sous, famille) {
    const h = document.createElement('div');
    h.className = 'sect';
    h.innerHTML = '<span>' + titre + '</span><em>' + sous + '</em>';
    liste.appendChild(h);
    for (const [id, m] of Object.entries(MODELS)) {
      if (m.famille !== famille) continue;
      const a = document.createElement('a');
      a.href = 'javascript:void 0';
      a.dataset.id = id;
      a.innerHTML = '<strong>' + m.titre + '</strong>';
      a.onclick = () => choisir(id);
      liste.appendChild(a);
    }
  }
  section('Permanents', 'rendus en cubes par CosmeticPixelAura', 'permanent');
  section('Ponctuels', 'aujourd\'hui en particules vanilla', 'ponctuel');

  const depart = location.hash.replace('#', '');
  choisir(MODELS[depart] ? depart : 'halo_continu');
})();
