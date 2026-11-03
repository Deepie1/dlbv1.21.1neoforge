#!/usr/bin/env python3
"""Build the French palette reference PDF directly from the canonical Java enums."""
from pathlib import Path
import re

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (BaseDocTemplate, Frame, PageTemplate, Paragraph,
                                PageBreak, Spacer, Table, TableStyle, Flowable)

ROOT = Path(__file__).resolve().parents[1]
ELEMENTS_JAVA = ROOT / "src/main/java/deepluckyblock/enchantments/DeepElement.java"
EFFECTS_JAVA = ROOT / "src/main/java/deepluckyblock/enchantments/WeaponVisualEffectId.java"
PDF_OUT = ROOT / "docs/Palette_couleurs_animations_elementaires.pdf"

NAVY = colors.HexColor("#1B2030")
INK = colors.HexColor("#252B3A")
MUTED = colors.HexColor("#687080")
PURPLE = colors.HexColor("#5E3AFF")
PALE = colors.HexColor("#F3F4F8")
BORDER = colors.HexColor("#D9DCE5")


def parse_java():
    element_source = ELEMENTS_JAVA.read_text(encoding="utf-8")
    effect_source = EFFECTS_JAVA.read_text(encoding="utf-8")
    elements = []
    ep = re.compile(r'^\s*(\w+)\("([^"\n]+)",\s*(0x[0-9A-Fa-f]{6}),\s*(0x[0-9A-Fa-f]{6}),\s*(0x[0-9A-Fa-f]{6})\)', re.M)
    for name, display, primary, secondary, highlight in ep.findall(element_source):
        elements.append(dict(name=name, display=display, primary=primary.upper(),
                             secondary=secondary.upper(), highlight=highlight.upper()))
    by_name = {e['name']: e for e in elements}
    fp = re.compile(r'^\s*(\w+)\((\d+),\s*Category\.(\w+),\s*(\d+),\s*(true|false),\s*DeepElement\.(\w+)\)[,;]', re.M)
    effects = []
    for name, number, category, priority, additive, element in fp.findall(effect_source):
        if element not in by_name:
            raise ValueError(f"Unknown element {element} for effect {name}")
        effects.append(dict(name=name, number=int(number), category=category,
                            element=by_name[element]))
    if len(elements) != 12:
        raise ValueError(f"Expected 12 elemental palettes, found {len(elements)}")
    if len(effects) != 40:
        raise ValueError(f"Expected 40 effects, found {len(effects)}")
    return elements, effects


class HexSwatch(Flowable):
    def __init__(self, hex_value, width=47*mm, height=8*mm):
        super().__init__()
        self.hex_value = '#' + hex_value.removeprefix('0x').removeprefix('0X').upper()
        self.width = width
        self.height = height

    def draw(self):
        rgb = colors.HexColor(self.hex_value)
        self.canv.setFillColor(rgb)
        self.canv.setStrokeColor(BORDER)
        self.canv.roundRect(0, 0, 17*mm, self.height, 1.2*mm, fill=1, stroke=1)
        r, g, b = rgb.red, rgb.green, rgb.blue
        luminance = 0.2126*r + 0.7152*g + 0.0722*b
        self.canv.setFillColor(colors.white if luminance < 0.48 else NAVY)
        self.canv.setFont(FONT_REGULAR, 7.1)
        self.canv.drawCentredString(8.5*mm, self.height/2 - 2.3, self.hex_value)


styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="TitleFR", parent=styles["Title"], fontName="DLBBold",
                          fontSize=23, leading=27, textColor=NAVY, alignment=TA_LEFT, spaceAfter=4*mm))
styles.add(ParagraphStyle(name="SubFR", parent=styles["Normal"], fontName="DLBRegular",
                          fontSize=9.2, leading=14, textColor=MUTED, spaceAfter=3*mm))
styles.add(ParagraphStyle(name="SectionFR", parent=styles["Heading2"], fontName="DLBBold",
                          fontSize=13.2, leading=16, textColor=NAVY, spaceBefore=1*mm, spaceAfter=3*mm))
styles.add(ParagraphStyle(name="BodyFR", parent=styles["BodyText"], fontName="DLBRegular",
                          fontSize=8.3, leading=12, textColor=INK, spaceAfter=2.2*mm))
styles.add(ParagraphStyle(name="TinyFR", parent=styles["BodyText"], fontName="DLBRegular",
                          fontSize=7.2, leading=9, textColor=MUTED))
styles.add(ParagraphStyle(name="CellFR", parent=styles["BodyText"], fontName="DLBRegular",
                          fontSize=8.0, leading=10, textColor=INK))
styles.add(ParagraphStyle(name="CellBoldFR", parent=styles["BodyText"], fontName="DLBBold",
                          fontSize=8.1, leading=10, textColor=INK))
styles.add(ParagraphStyle(name="FootFR", parent=styles["Normal"], fontName="DLBRegular",
                          fontSize=7.0, leading=8, textColor=MUTED, alignment=TA_CENTER))


class PaletteDoc(BaseDocTemplate):
    def __init__(self, filename):
        super().__init__(filename, pagesize=A4, leftMargin=15*mm, rightMargin=15*mm,
                         topMargin=15*mm, bottomMargin=14*mm, title="Palettes de couleurs des animations élémentaires — T374",
                         author="Deep Lucky Block")
        frame = Frame(self.leftMargin, self.bottomMargin, self.width, self.height,
                      leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0, id="main")
        self.addPageTemplates(PageTemplate(id="pages", frames=[frame], onPage=self._page))

    def _page(self, canvas, doc):
        canvas.saveState()
        w, h = A4
        canvas.setFillColor(NAVY)
        canvas.rect(0, h-4*mm, w, 4*mm, stroke=0, fill=1)
        canvas.setStrokeColor(BORDER)
        canvas.line(15*mm, 10*mm, w-15*mm, 10*mm)
        canvas.setFont(FONT_REGULAR, 7)
        canvas.setFillColor(MUTED)
        canvas.drawString(15*mm, 6.2*mm, "RÉFÉRENCE CHROMATIQUE · MISE À JOUR T374")
        canvas.drawRightString(w-15*mm, 6.2*mm, f"{doc.page} / 3")
        canvas.restoreState()


def palette_table(elements):
    rows = [[Paragraph("Famille", styles["CellBoldFR"]),
             Paragraph("Primaire · cœur", styles["CellBoldFR"]),
             Paragraph("Secondaire · traînée", styles["CellBoldFR"]),
             Paragraph("Surbrillance · accent", styles["CellBoldFR"])]]
    for e in elements:
        rows.append([Paragraph(e['display'], styles["CellBoldFR"]),
                     HexSwatch(e['primary']), HexSwatch(e['secondary']), HexSwatch(e['highlight'])])
    t = Table(rows, colWidths=[28*mm, 49*mm, 49*mm, 49*mm], rowHeights=[9*mm] + [11.2*mm]*len(elements), repeatRows=1)
    t.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), PALE),
        ('TEXTCOLOR', (0,0), (-1,0), NAVY),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('LEFTPADDING', (0,0), (-1,-1), 3*mm),
        ('RIGHTPADDING', (0,0), (-1,-1), 2*mm),
        ('GRID', (0,0), (-1,-1), 0.35, BORDER),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#FAFAFC')]),
    ]))
    return t


def effect_table(effects):
    rows = [[Paragraph("ID / effet", styles["CellBoldFR"]),
             Paragraph("Famille", styles["CellBoldFR"]),
             Paragraph("Primaire", styles["CellBoldFR"]),
             Paragraph("Secondaire", styles["CellBoldFR"]),
             Paragraph("Accent recalculé", styles["CellBoldFR"])]]
    for fx in effects:
        e = fx['element']
        rows.append([Paragraph(f"T{fx['number']:02d} · {fx['name']}", styles["CellFR"]),
                     Paragraph(e['display'], styles["CellFR"]),
                     HexSwatch(e['primary'], width=35*mm, height=7*mm),
                     HexSwatch(e['secondary'], width=35*mm, height=7*mm),
                     HexSwatch(e['highlight'], width=35*mm, height=7*mm)])
    t = Table(rows, colWidths=[55*mm, 22*mm, 34*mm, 34*mm, 35*mm],
              rowHeights=[8*mm] + [8.1*mm]*len(effects), repeatRows=1)
    t.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), PALE),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('LEFTPADDING', (0,0), (-1,-1), 2*mm),
        ('RIGHTPADDING', (0,0), (-1,-1), 1.5*mm),
        ('GRID', (0,0), (-1,-1), 0.32, BORDER),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#FAFAFC')]),
    ]))
    return t


def main():
    global FONT_REGULAR, FONT_BOLD
    font_reg = Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
    font_bold = Path('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf')
    if font_reg.exists() and font_bold.exists():
        pdfmetrics.registerFont(TTFont('DLBRegular', str(font_reg)))
        pdfmetrics.registerFont(TTFont('DLBBold', str(font_bold)))
    else:
        pdfmetrics.registerFont(TTFont('DLBRegular', '/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf'))
        pdfmetrics.registerFont(TTFont('DLBBold', '/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf'))
    FONT_REGULAR, FONT_BOLD = 'DLBRegular', 'DLBBold'
    elements, effects = parse_java()
    story = [Spacer(1, 3*mm),
             Paragraph("Palettes des animations élémentaires", styles['TitleFR']),
             Paragraph("Référence exacte des couleurs de combat · mise à jour corrective T374", styles['SubFR']),
             Paragraph("Chaque rôle d’animation reprend sa couleur de famille : <b>primaire</b> pour le cœur, <b>secondaire</b> pour la traînée et <b>surbrillance</b> pour l’accent. Les carrés donnent les valeurs HEX effectivement utilisées.", styles['BodyFR']),
             Paragraph("Verrouillage T374 : les variantes n’altèrent que la luminosité; aucun décalage de teinte ne peut injecter une couleur étrangère. Les accents des 40 effets sont recalculés depuis la surbrillance de leur élément. Les armes multi-éléments alternent entre familles au sein d’un même rôle, sans mélanger leurs teintes RGB. Chaos conserve son traitement prismatique dédié.", styles['BodyFR']),
             Paragraph("Palettes canoniques", styles['SectionFR']), palette_table(elements), Spacer(1, 3*mm),
             Paragraph("Contrôle Sang : primaire, secondaire et accent restent rouges (aucun orange ajouté par les animations).", styles['TinyFR']),
             PageBreak(),
             Paragraph("Accents recalculés · effets 01 à 20", styles['SectionFR']),
             Paragraph("Les références d’effet ci-dessous pointent vers les trois couleurs canoniques de leur famille; la dernière colonne est l’accent recalculé (surbrillance).", styles['SubFR']),
             effect_table(effects[:20]), PageBreak(),
             Paragraph("Accents recalculés · effets 21 à 40", styles['SectionFR']),
             Paragraph("Les mêmes rôles de couleur sont appliqués à toutes les animations et aux couches de pixels secondaires.", styles['SubFR']),
             effect_table(effects[20:]), Spacer(1, 2*mm),
             Paragraph("Source des données : DeepElement.java et WeaponVisualEffectId.java · T374", styles['FootFR'])]
    PDF_OUT.parent.mkdir(parents=True, exist_ok=True)
    PaletteDoc(str(PDF_OUT)).build(story)
    print(f"Generated {PDF_OUT} ({len(elements)} families, {len(effects)} effects)")


if __name__ == '__main__':
    main()
