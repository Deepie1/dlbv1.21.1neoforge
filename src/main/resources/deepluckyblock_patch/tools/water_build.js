function buildWater(variant,t,salt=5252){const out=[];if(t<=0||t>=1)return out;
 const box=(f,y,l,hx,hy,hz,rgb,a,roll=0)=>{if(a>14&&hx>.0005&&hy>.0005&&hz>.0005&&Number.isFinite(f)&&Number.isFinite(y)&&Number.isFinite(l))
   out.push({x:f,y,z:l,hx,hy,hz,rgb,a:Math.min(255,a),roll})};
 const col=(dark,light,u)=>mix(dark,light,clamp(u)),TAU=Math.PI*2,frc=x=>x-Math.floor(x);
 // Teintes des VRAIS blocs du sol (côté Java : WaterTerrainVfx réimprime les BlockState réels).
 const GRASS=0x618E42,DIRT=0x6B4B2A,STONE=0x7E7E77,SAND=0xD9CFA2;

 // ═══════════ Vrais blocs du sol — parité Java WaterTerrainVfx ═══════════
 // mode 0 = la vague POUSSE (pickup selon la position le long de l'axe), mode 1 = le tourbillon AVALE.
 const waterDebris=(count,mode,depth)=>{
  for(let i=0;i<count;i++){const h=hash(salt,21000+i*97),h2=hash(salt,22000+i*53),h3=hash(salt,23000+i*71),
    gdist=.40+h*(mode?3.60:3.10),bearing=h2*TAU,
    along=Math.cos(bearing)*gdist,lateral=Math.sin(bearing)*gdist,
    pickup=mode? Math.min(.70,.05+.52*(gdist/4.0)+.10*h2)
               : Math.min(.74,.12+clamp((along+3.40)/6.20)*.46+(h2-.5)*.05),
    flight=mode?(.34+h*.26):(.30+h*.22),
    u0=(t-pickup)/flight;
   if(u0<=0)continue;                        // l'eau ne l'a pas encore atteint : le sol est intact
   const u=u0-Math.floor(u0),
     tear=smooth(u/.18),ride=smooth((u-.12)/.58),land=smooth((u-.84)/.16),
     base=.28+h2*.22,size=base*(.25+.75*tear)*(1-land*.30);
   let px,pz,yy,tumble;
   if(mode){const pull=tear*(.28+.72*ride),
      turns=1.30+h3*1.10,ang=bearing+pull*u*turns*TAU,
      r1=.22+.70*h3,target=r1*(.85+.26*Math.sin(u*8+bearing*5)),
      radius=lerp(gdist,target,pull),spit=smooth((u-.88)/.12);
    px=Math.cos(ang)*radius;pz=Math.sin(ang)*radius;
    yy=-1.05+tear*.10-ride*depth*(.55+.45*pull)*(1-spit)+spit*(6.40*(.70+h*.60));
    tumble=tear*(u*8+bearing)*(1.30+h3*2.70)}
   else{const push=tear*(.35+.65*ride),turns=.35+h3*.55,
      r1=clamp(gdist*.80+h3*1.30,.45,4.20),
      a2=along+push*(2.30+2.40*turns),
      l2=lateral+push*(r1-gdist)*.35*(lateral>=0?1:-1),
      crest=Math.sin(clamp(ride)*Math.PI);
    px=a2;pz=l2;
    yy=-1.05+tear*.12+crest*depth*.46*(.70+h*.60)-land*.40;
    tumble=tear*(u*5+lateral)*(1.30+h3*2.70)*.75}
   if(size<=.05)continue;
   box(px,yy,pz,size*.58,size*.44,size*.52,
       i%4===0?GRASS:(i%4===1?DIRT:(i%4===2?STONE:SAND)),
       smooth(u/.06)*215*(1-land*.40),tumble)}};

 // ══════════════════════ Ⅰ. RAZ-DE-MARÉE — mur d'eau qui marche ══════════════════════
 if(variant===0){
  const a=Math.round(245*smooth(t/.05)*(1-smooth((t-.90)/.10)));if(a<=4)return out;
  const H=4.60,HW=3.40,COLS=19,
    draw=smooth(t/.18),lift=smooth((t-.08)/.34),march=smooth((t-.12)/.46),
    curl=smooth((t-.34)/.26),burst=smooth((t-.50)/.16)*(1-smooth((t-.82)/.18)),
    ebb=smooth((t-.66)/.30),body=1-ebb*.86,front=lerp(-3.40,2.80,march);

  // 1. retrait : trois arcs de succion (72)
  if(draw<1||ebb>0){const pull=Math.max(1-draw,ebb*.85);
   for(let arc=0;arc<3;arc++){const radius=(1.10+arc*.78)*(.65+.55*pull);
    for(let i=0;i<24;i++){const u=(i/23)*2-1,h=hash(salt,7100+arc*61+i*7),
      size=(.070+.045*h)*(.55+.45*pull);
     box(-radius*(.55+.45*Math.cos(u*1.4))-pull*.70,
         -1.12+.05*h+.08*Math.sin(t*7+i*.9),u*radius*1.35,
         size*1.5,size*.42,size*1.5,col(0x14557F,0x1E7BA8,h),a*pull*.62)}}}

  // 2. le mur : 19 colonnes pleines, cisaillées, + lèvre enroulée
  if(body>.02){for(let c=0;c<COLS;c++){
   const u=(c/(COLS-1))*2-1,hc=hash(salt,7300+c*29),hc2=hash(salt,7400+c*53),
     profile=(1-u*u*.74)*(.86+.26*hc),columnH=H*lift*profile*body;
   if(columnH<.12)continue;
   const lateral=u*HW,forward=front-(u*u)*1.15+(hc2-.5)*.22,
     steps=Math.max(3,Math.floor(columnH/.36));
   for(let s=0;s<steps;s++){const v=(s+.5)/steps,
     shear=curl*v*v*1.45+march*v*.38,y=-1.12+columnH*v,
     half=(.235-.085*v)*(.90+.22*hc),depth=.30+.26*(1-v);
    box(forward+shear,y,lateral+Math.sin(v*3.1+hc*6)*.10,
        depth,.21+.06*(1-v),half,
        col(col(0x0B3550,0x1E7BA8,v*.85),0x37A6D6,v*v),
        a*body*(.62+.34*v),-curl*v*.85)}
   if(curl>.03){for(let s=0;s<5;s++){const v=s/4,
     arcA=curl*(.35+v*1.95),r=.52+.20*hc,
     ly=-1.12+columnH+Math.sin(arcA)*r*.70-v*.10,
     lf=forward+curl*1.45+(1-Math.cos(arcA))*r*1.35;
    box(lf,ly,lateral,.30,.17,.215+.03*hc,col(0x37A6D6,0xE8FBFF,.30+.60*v),
        a*body*curl*(.80-.25*v),-arcA)}}}}

  // 3. écume : moutons discontinus (58 tirages, ~38 % retenus)
  if(curl>.05&&ebb<.85){for(let i=0;i<58;i++){
   const h=hash(salt,7600+i*17);if(h>.62)continue;
   const h2=hash(salt,7700+i*31),h3=hash(salt,7800+i*11),u=h2*2-1,
     columnH=H*lift*(1-u*u*.74)*body,
     lf=front-(u*u)*1.15+curl*(1.10+.90*h3),
     ly=-1.12+columnH*(.86+.22*h3)+curl*.22,size=.055+.055*h3;
   box(lf,ly,u*HW,size,size*(.70+.40*h),size,col(0xE8FBFF,0xF6FEFF,h3),a*(1-ebb)*.92)}}

  // 4. gerbes balistiques (46)
  if(burst>.01){for(let i=0;i<46;i++){
   const h=hash(salt,7900+i*13),h2=hash(salt,8000+i*23),h3=hash(salt,8100+i*37),
     birth=.50+h*.18,life=(t-birth)/.30;
   if(life<=0||life>=1)continue;
   const speed=2.10+2.60*h2,side=(h3*2-1)*2.60,
     dF=front+life*speed*(.45+.55*h2),
     dY=-1.12+life*(3.60+1.70*h)-life*life*(4.30+1.60*h2);
   if(dY<-1.16)continue;
   const size=(.055+.050*h3)*(1-life*.45);
   box(dF,dY,side*(.55+.45*life),size,size*1.30,size,
       col(0xA9E6F7,0xF6FEFF,h2*.75),a*burst*(1-life*.80),life*5)}}

  // 5. reflux : trois rides concentriques
  if(ebb>.02){for(let ring=0;ring<3;ring++){
   const phase=ebb-ring*.14;if(phase<=0)continue;
   const radius=.90+phase*(4.60+ring*.85),count=30+ring*6,
     fade=(1-smooth(phase*1.12))*(1-ebb*.25);
   for(let i=0;i<count;i++){const ang=i*TAU/count+ring*.22,h=hash(salt,8200+ring*97+i*5);
    if(h>.86)continue;
    const r=radius*(.95+.10*Math.sin(ang*3+ebb*5)),size=.085+.055*h;
    box(Math.cos(ang)*r,-1.14+.045*Math.sin(ang*5+t*12),Math.sin(ang)*r,
        size*1.45,.030+.020*h,size*1.45,col(0x62C7E8,0xE8FBFF,h),a*fade*.70)}}}

  // 7. ruissellement sur la face du mur
  if(lift>.12&&body>.05){for(let i=0;i<34;i++){
   const h=hash(salt,8400+i*23),h2=hash(salt,8500+i*41),h3=hash(salt,8600+i*13),u=h2*2-1,
     columnH=H*lift*(1-u*u*.74)*body;
   if(columnH<.35)continue;
   const fall=frc(t*(.70+.55*h3)+h),fy=-1.12+columnH*(1-fall*.92),
     forward=front-(u*u)*1.15+curl*.55+(h3-.5)*.20,len=.10+.16*(1-fall);
   box(forward,fy,u*HW,.055,len,.045+.025*h3,
       col(0x62C7E8,0xE8FBFF,.35+.55*(1-fall)),a*body*(.55-.30*fall))}}

  // 8. sol mouillé devant le front
  if(march>.02){const wetFront=front+1.10;
   for(let i=0;i<40;i++){const h=hash(salt,8700+i*19),h2=hash(salt,8800+i*7),
     along=-3.60+(wetFront+3.60)*h,lateral=(h2*2-1)*HW*(1.05-.25*h),fade=1-ebb*.80;
    if(fade<=.04)continue;
    box(along,-1.16+.015*h,lateral,.30+.18*h,.022,.30+.18*h2,
        col(0x14557F,0x1E7BA8,h*.60),a*fade*.34)}}

  // 6. vrais blocs du sol poussés par le front
  waterDebris(84,0,H);
  return out;
 }

 // ══════════════════════ Ⅱ. MAELSTRÖM ABYSSAL — entonnoir + geyser ══════════════════════
 if(variant===1){
  const a=Math.round(245*smooth(t/.05)*(1-smooth((t-.93)/.07)));if(a<=4)return out;
  const R=4.35,D=3.30,GH=9.00,RINGS=5,
    open=smooth(t/.20),spin=t*10,implode=smooth((t-.60)/.20),
    geyser=smooth((t-.76)/.14)*(1-smooth((t-.94)/.06)),life=1-implode;

  // 1. cinq anneaux, rotation différentielle (×1 au bord → ×3,4 à la gorge)
  if(life>.02){for(let ring=0;ring<RINGS;ring++){
   const k=ring/(RINGS-1),radius=R*(1-k*.80)*open*(1-implode*(.25+.70*k));
   if(radius<.10)continue;
   const depth=-.95-D*k*k*open,omega=1+k*2.40,count=Math.max(18,Math.round(44-ring*4));
   for(let i=0;i<count;i++){const h=hash(salt,9100+ring*211+i*7),
     ang=i*TAU/count+spin*omega+ring*.47,
     r=radius*(.90+.16*Math.sin(ang*3+t*9+ring)),
     y=depth+.10*Math.sin(ang*4+t*11)-.05*h,
     size=(.185-.055*k)*(.85+.30*h);
    box(Math.cos(ang)*r,y,Math.sin(ang)*r,size*1.55,size*.52,size*1.05,
        col(col(0x62C7E8,0x1E7BA8,k),0x0B3550,k*k*.85),
        a*life*(.52+.40*(1-k)),-(.42+.95*k))}}}

  // 2. trois bras d'écume en spirale logarithmique (1,6 tour)
  if(life>.02){for(let arm=0;arm<4;arm++){const armP=arm*(TAU/4);
   for(let i=0;i<30;i++){const v=i/29,h=hash(salt,9400+arm*131+i*17);
    if(h>.90)continue;
    const radius=R*(1-v*.88)*open*(1-implode*(.20+.75*v)),
      ang=armP+v*TAU*1.60+spin*(1+v*2.40),
      y=-.95-D*v*v*open+.06*Math.sin(v*20+t*14),
      size=(.085-.030*v)*(.80+.45*h);
    box(Math.cos(ang)*radius,y,Math.sin(ang)*radius,size*1.35,size*.55,size*1.35,
        col(0xE8FBFF,0xA9E6F7,v*.80),a*life*(.90-.35*v))}}}

  // 3. la gorge : quatre étages sombres qui pulsent
  if(life>.02){const throat=.62*open*(1-implode);
   for(let lvl=0;lvl<4;lvl++){const lv=lvl/3,r=throat*(1-lv*.55);
    if(r<.05)continue;
    const y=-.95-D*open*(.72+.26*lv),count=14-lvl*2;
    for(let i=0;i<count;i++){const ang=i*TAU/count+spin*(3.4+lv*1.6),
      pulse=.88+.18*Math.sin(t*24+lv*2.4);
     box(Math.cos(ang)*r*pulse,y,Math.sin(ang)*r*pulse,.115,.085,.115,
         col(0x0B3550,0x14557F,lv*.45),a*life*(.95-.20*lv))}}}

  // 4. couronne du bord
  if(open>.05&&life>.02){for(let i=0;i<44;i++){
   const h=hash(salt,9700+i*19),ang=i*TAU/44+spin*.85,
     r=R*open*(1.02+.08*Math.sin(ang*5+t*8))*(1-implode*.30),
     y=-1.02+.30*open*(.55+.45*Math.sin(ang*3-t*10))+.06*h,size=.095+.055*h;
   box(Math.cos(ang)*r,y,Math.sin(ang)*r,size*1.40,size*.70,size*.95,
       col(0x37A6D6,0xE8FBFF,h*.65),a*life*.78,-.35)}}

  // 5. geyser : colonne construite de bas en haut + couronne en parapluie
  if(geyser>.01){const jet=smooth((t-.76)/.10);
   for(let s=0;s<26;s++){const v=(s+.5)/26,h=hash(salt,9900+s*23),
     local=clamp((jet-v*.55)/.45);
    if(local<=0)continue;
    const y=-1.10+GH*v*local,r=(.78-.46*v)*(.85+.30*h)*local,
      count=Math.max(5,Math.round(11-v*5));
    for(let i=0;i<count;i++){const ang=i*TAU/count+spin*(1.6+v*1.2),
      size=(.145-.070*v)*(.85+.28*h);
     box(Math.cos(ang)*r,y,Math.sin(ang)*r,size,size*1.55,size,
         col(col(0x1E7BA8,0x62C7E8,v),0xF6FEFF,v*v*.80),a*geyser*(.92-.30*v))}}
   const crown=smooth((t-.84)/.12);
   if(crown>.02){for(let i=0;i<40;i++){
    const h=hash(salt,10100+i*29),h2=hash(salt,10200+i*13),ang=i*TAU/40+h*.5,
      reach=crown*(1.50+2.40*h2),
      y=-1.10+GH*(.92+.08*h)-crown*crown*(4.20+3.10*h2);
    if(y<-1.14)continue;
    const size=.065+.050*h2;
    box(Math.cos(ang)*reach,y,Math.sin(ang)*reach,size,size*1.25,size,
        col(0xA9E6F7,0xF6FEFF,h),a*geyser*(1-crown*.55),crown*3)}}}

  // 7. nappe de surface (recouvre la jonction avec le trou de blocs fantômes)
  if(open>.04){const sheet=life*(.55+.45*open);
   for(let ring=0;ring<3;ring++){const k=ring/2,radius=R*(1.02+.16*k)*open,count=38+ring*8;
    for(let i=0;i<count;i++){const h=hash(salt,10400+ring*137+i*11);
     if(h>.88)continue;
     const ang=i*TAU/count+spin*(.55-.12*k)+ring*.31,
       r=radius*(.97+.07*Math.sin(ang*4+t*7)),
       y=-1.15+.055*Math.sin(ang*6+t*9)+.02*h,size=.115+.070*h;
     box(Math.cos(ang)*r,y,Math.sin(ang)*r,size*1.50,.028+.018*h,size*1.10,
         col(col(0x1E7BA8,0x62C7E8,k),0xE8FBFF,h*.45),a*sheet*(.62-.14*k),-ang)}}}

  // 8. bulles remontant de la gorge
  if(open>.08){for(let i=0;i<30;i++){
   const h=hash(salt,10600+i*29),h2=hash(salt,10700+i*17),h3=hash(salt,10800+i*43),
     climb=frc(t*(.42+.38*h3)+h),ang=h2*TAU+spin*1.20*climb,
     r=(.22+.95*h2)*open*(1-climb*.55),
     y=-.95-D*open*(1-climb)+.35*climb,size=(.045+.040*h3)*(.60+.55*climb);
   box(Math.cos(ang)*r,y,Math.sin(ang)*r,size,size,size,
       col(0xA9E6F7,0xF6FEFF,climb*.70),a*life*(.30+.55*climb))}}

  // ═══════ v1.3 — L'INTÉRIEUR DU TROU (parité Java : blocs 9, 10, 11) ═══════
  // 9. paroi volumétrique : onze nappes hélicoïdales, 1,9 tour du bord à la gorge
  if(life>.02&&open>.03){const sheets=11,steps=34;
   for(let sh=0;sh<sheets;sh++){const sP=sh*(TAU/sheets),sJ=hash(salt,11000+sh*311);
    for(let i=0;i<steps;i++){const v=i/(steps-1),h=hash(salt,11100+sh*173+i*29);
     if(h>.93-.10*v)continue;
     const radius=R*(1-v*.86)*open*(1-implode*(.22+.74*v))*(.95+.09*Math.sin(v*9+t*12+sJ*6));
     if(radius<.06)continue;
     const ang=sP+v*TAU*1.90+spin*(1+v*2.40),
       y=-.95-D*v*v*open+.07*Math.sin(v*16+t*13+sJ*4),
       size=(.165-.095*v)*(.80+.45*h);
     box(Math.cos(ang)*radius,y,Math.sin(ang)*radius,size*2.05,size*.42,size*.95,
         col(col(0x37A6D6,0x1E7BA8,v*.85),0x0B3550,v*v*.80),
         a*life*(.58+.30*(1-v)),-(.38+1.05*v))}}}

  // 10. remplissage : 200 gouttes en suspension DANS le cône (rayon tiré en sqrt)
  if(life>.02&&open>.05){for(let i=0;i<200;i++){
   const h=hash(salt,12000+i*37),h2=hash(salt,12200+i*53),h3=hash(salt,12400+i*19),v=h,
     wall=R*(1-v*.86)*open*(1-implode*(.22+.74*v));
   if(wall<.08)continue;
   const r=wall*Math.sqrt(h2)*.94,
     ang=h3*TAU+spin*(1+v*2.40)*(.80+.40*h2),
     y=-.95-D*v*v*open+.22*Math.sin(t*15+h3*9),
     size=(.040+.065*h2)*(1-.35*v);
   box(Math.cos(ang)*r,y,Math.sin(ang)*r,size,size*1.30,size,
       col(col(0x62C7E8,0x1E7BA8,v*.70),0xA9E6F7,h3*.55),
       a*life*(.42+.38*(1-v)))}}

  // 11. le cœur à trois échelles (0,34 / 0,155 / 0,055) + œil noir
  if(life>.02&&open>.06){const core=open*(1-implode*.55),fy=-.95-D*open;
   // (a) six grosses dalles lentes
   for(let i=0;i<6;i++){const h=hash(salt,13000+i*101),ang=i*TAU/6+spin*1.70,
     r=(.52+.30*h)*core,y=fy+.28+.16*Math.sin(t*9+i*1.7),size=.34*(.85+.30*h);
    box(Math.cos(ang)*r,y,Math.sin(ang)*r,size*1.45,size*.50,size*1.05,
        col(0x14557F,0x1E7BA8,h*.55),a*life*.90,-.85)}
   // (b) vingt-quatre blocs moyens en double hélice
   for(let i=0;i<24;i++){const h=hash(salt,13200+i*59),v=i/23,
     ang=v*TAU*2.40+spin*3.10+(i%2)*Math.PI,
     r=(.20+.46*(1-v))*core,y=fy+.10+v*(1.45*core)+.07*Math.sin(t*17+i),
     size=.155*(.80+.45*h);
    box(Math.cos(ang)*r,y,Math.sin(ang)*r,size*1.30,size*.85,size*1.30,
        col(0x1E7BA8,0x62C7E8,v*.70),a*life*(.88-.20*v),-.55)}
   // (c) quatre-vingt-dix éclats rapides
   for(let i=0;i<90;i++){const h=hash(salt,13500+i*23),h2=hash(salt,13700+i*41),
     h3=hash(salt,13900+i*13),ang=h*TAU+spin*(4.20+2.60*h2),
     r=(.10+.72*h2)*core,y=fy+.05+h3*(1.90*core)+.10*Math.sin(t*26+h*11),
     size=.055*(.70+.60*h3);
    box(Math.cos(ang)*r,y,Math.sin(ang)*r,size,size*1.45,size,
        col(0xA9E6F7,0xF6FEFF,h3*.75),a*life*(.60+.35*h2))}
   // (d) l'œil : noyau noir pulsant au fond
   const eye=.30*core*(.90+.14*Math.sin(t*20));
   for(let i=0;i<16;i++){const h=hash(salt,14100+i*71),ang=i*TAU/16+spin*.90,
     lift=(i%2===0)?0:.14,size=.105*(.85+.30*h);
    box(Math.cos(ang)*eye,fy+.08+lift,Math.sin(ang)*eye,size,size*1.20,size,
        col(0x0B3550,0x14557F,h*.30),a*life*.96)}}

  // 6. vrais blocs du sol avalés puis recrachés
  waterDebris(92,1,D);
  return out;
 }

 // ══════════════════════ Ⅲ. GIVRE DU PERMAFROST — étoile, pics, prison, éclats ══════════════════════
 const a=Math.round(245*smooth(t/.04)*(1-smooth((t-.92)/.08)));if(a<=4)return out;
 const creep=smooth(t/.22),grow=smooth((t-.10)/.36),cage=smooth((t-.30)/.36),
   crack=smooth((t-.60)/.20),shatter=smooth((t-.78)/.14),solid=1-shatter,
   SPIKE_H=3.20,SPIKES=9;

 // 1. étoile de givre : huit branches + ramifications
 for(let b=0;b<8;b++){const base=b*TAU/8+hash(salt,11000+b)*.35;
  for(let s=0;s<16;s++){const v=(s+1)/16,local=clamp((creep-v*.55)/.45);
   if(local<=0)continue;
   const h=hash(salt,11100+b*97+s*11),ang=base+(h-.5)*.30*v,r=v*4.60*local,
     size=(.115-.055*v)*(.80+.40*h);
   box(Math.cos(ang)*r,-1.15+.02*h,Math.sin(ang)*r,
       size*1.30,.032+.022*h,size*1.30,col(0xBFEBFA,0xE4F7FF,h),
       a*local*(.92-.30*v)*solid);
   if(s%3===2&&h>.35){const side=hash(salt,11200+b*31+s)>.5?1:-1,
     sAng=ang+side*.62,sr=r*.82,len=.42*(1-v)*local;
    box(Math.cos(sAng)*(sr+len),-1.15+.02*h,Math.sin(sAng)*(sr+len),
        .070,.028,.070,col(0x7FC9E8,0xBFEBFA,h),a*local*.70*solid)}}}

 // 2. couronne de neuf pics
 for(let p=0;p<SPIKES;p++){const h=hash(salt,11400+p*53),h2=hash(salt,11500+p*29),
   ang=p*TAU/SPIKES+(h-.5)*.25,radius=1.70+1.05*h2,
   spikeH=SPIKE_H*(.52+.48*h)*grow*(1-shatter*.85);
  if(spikeH<.10)continue;
  const steps=Math.max(4,Math.floor(spikeH/.26)),tilt=.16+.22*h2;
  for(let s=0;s<steps;s++){const v=(s+.5)/steps,y=-1.15+spikeH*v,
    r=radius+tilt*v*v*1.30,half=(.205-.165*v)*(.85+.30*h);
   box(Math.cos(ang)*r,y,Math.sin(ang)*r,
       half*1.20,spikeH/steps*.62,half*1.20,
       col(col(0x2E6F93,0x7FC9E8,v),0xE4F7FF,v*v*.70),
       a*(.70+.28*v)*(1-shatter*.70),tilt*v)}}
 // trois pics majeurs autour de la cible
 for(let p=0;p<3;p++){const h=hash(salt,11700+p*71),ang=p*TAU/3+.4,
   spikeH=(2.05+.75*h)*grow*(1-shatter*.80),steps=Math.max(5,Math.floor(spikeH/.24));
  for(let s=0;s<steps;s++){const v=(s+.5)/steps,y=-1.15+spikeH*v,r=.86+v*.30,
    half=(.235-.185*v)*(.90+.20*h);
   box(Math.cos(ang)*r,y,Math.sin(ang)*r,half*1.25,spikeH/steps*.60,half*1.25,
       col(0x7FC9E8,0xE4F7FF,v*.80),a*(.72+.26*v)*(1-shatter*.70),.24*v)}}

 // 3. prison de glace : trois couches × quatre niveaux + veines de fracture
 if(cage>.02&&solid>.02){const shell=.92*cage,shiver=crack*.045*Math.sin(t*52);
  for(let layer=0;layer<3;layer++){const lv=layer/2,r=shell*(.62+.38*lv),count=18-layer*3;
   for(let i=0;i<count;i++){const h=hash(salt,11900+layer*101+i*13),
     ang=i*TAU/count+lv*.4+shiver;
    for(let level=0;level<4;level++){const ly=-.95+level*.52,
      squeeze=1-Math.abs(level-1.5)*.18,size=(.165+.055*h)*squeeze;
     box(Math.cos(ang)*r*squeeze,ly+shiver*.5,Math.sin(ang)*r*squeeze,
         size,size*1.05,size,col(0x7FC9E8,0xBFEBFA,h*.70),
         a*cage*solid*(.38+.22*lv))}}}
  if(crack>.03){for(let i=0;i<22;i++){
   const h=hash(salt,12100+i*37),h2=hash(salt,12200+i*17);
   if(h>crack)continue;
   const ang=h2*TAU,ly=-.90+h*2.05,r=shell*(.58+.40*h2);
   box(Math.cos(ang)*r,ly,Math.sin(ang)*r,.028,.165+.120*h2,.028,
       col(0x2E6F93,0x0B3550,.45),a*crack*solid*.85,(h2-.5)*1.6)}}}

 // 4. éclatement : soixante éclats + anneau de froid
 if(shatter>.01){for(let i=0;i<60;i++){
  const h=hash(salt,12400+i*19),h2=hash(salt,12500+i*29),h3=hash(salt,12600+i*7),
    ang=h2*TAU,r=shatter*(1.40+3.20*h),
    y=-.70+h3*1.70+shatter*(1.70+1.10*h2)-shatter*shatter*(3.40+2.10*h);
  if(y<-1.16)continue;
  const size=(.085+.075*h3)*(1-shatter*.42),spin2=shatter*(7+9*h2);
  box(Math.cos(ang)*r,y,Math.sin(ang)*r,size*1.30,size*.70,size,
      col(col(0x7FC9E8,0xBFEBFA,h3),0xE4F7FF,h2*.55),
      a*(1-shatter*.72),spin2*.55)}
  for(let i=0;i<34;i++){const h=hash(salt,12800+i*11),ang=i*TAU/34,
    r=.80+shatter*(4.40+1.10*h);
   box(Math.cos(ang)*r,-1.14+.03*h,Math.sin(ang)*r,.115,.030,.115,
       col(0xE4F7FF,0xF6FEFF,h),a*(1-shatter)*.80)}}

 // 5. brume de froid au sol (recouvre la découpe du sol gelé)
 if(creep>.03){const haze=creep*(1-shatter*.55);
  for(let i=0;i<46;i++){const h=hash(salt,13000+i*31),h2=hash(salt,13100+i*17),h3=hash(salt,13200+i*53),
    drift=frc(t*(.10+.09*h3)+h),ang=h2*TAU+drift*.85,r=(.70+3.80*h)*creep,
    y=-1.14+.10*h3+.05*Math.sin(t*4+h*9),size=.16+.14*h3;
   box(Math.cos(ang)*r,y,Math.sin(ang)*r,size*1.45,.030+.022*h2,size*1.45,
       col(0xBFEBFA,0xE4F7FF,h2),a*haze*(.26-.10*(r/4.50)))}}

 // 6. cristaux en suspension autour de la prison
 if(cage>.04&&solid>.04){for(let i=0;i<26;i++){
  const h=hash(salt,13400+i*37),h2=hash(salt,13500+i*23),h3=hash(salt,13600+i*11),
    orbit=t*(.55+.70*h3)+h2*TAU,r=1.05+1.45*h,
    y=-.85+1.90*h2+.14*Math.sin(t*3.4+h*7),size=.045+.055*h3;
  box(Math.cos(orbit)*r,y,Math.sin(orbit)*r,size,size*1.70,size,
      col(0x7FC9E8,0xE4F7FF,h3),a*cage*solid*(.45+.35*h),t*1.8+h*6)}}
 return out;
}
