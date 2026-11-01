from pathlib import Path
import io
import cairosvg
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
assets = root / "assets"
assets.mkdir(exist_ok=True)
svg = (root / "branding" / "app_icon.svg").read_bytes()

def render(size):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg, output_width=size, output_height=size))).convert("RGBA")

icon = render(1024)
icon.save(assets / "icon.png")
icon.resize((512,512),Image.Resampling.LANCZOS).convert("RGB").save(assets / "play_store_icon.png")
foreground = Image.new("RGBA", (1024,1024), (0,0,0,0))
# A compact full-bleed icon reduced into the adaptive icon's safe center.
foreground.alpha_composite(icon.resize((720,720), Image.Resampling.LANCZOS), (152,152))
foreground.save(assets / "icon_foreground.png")

feature = Image.new("RGB",(1024,500),(9,35,85))
d = ImageDraw.Draw(feature)
for y in range(500):
    t = y / 500
    d.line((0,y,1024,y),fill=(int(8+17*t),int(79+28*t),int(166+44*t)))
feature.paste(icon.resize((400,400),Image.Resampling.LANCZOS), (46,50), icon.resize((400,400),Image.Resampling.LANCZOS))
try:
    bold="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    font=ImageFont.truetype(bold,54)
    small=ImageFont.truetype(bold,28)
except OSError:
    font=ImageFont.load_default()
    small=ImageFont.load_default()
d.text((470,151),"CHAMPAK'S",fill=(255,255,255),font=font)
d.text((470,222),"TV REMOTE",fill=(216,246,255),font=font)
d.text((475,310),"Your phone. Your TV.",fill=(231,247,255),font=small)
feature.save(assets / "feature_graphic.png")
print("Generated Android and store artwork:",*[f.name for f in assets.iterdir()])
