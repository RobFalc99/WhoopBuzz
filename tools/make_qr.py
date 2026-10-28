"""Generate docs/qr.svg and docs/qr.png pointing to the GitHub Pages install page."""
import sys
import qrcode
import qrcode.image.svg

url = sys.argv[1] if len(sys.argv) > 1 else "https://robfalc99.github.io/WhoopBuzz/"
qrcode.make(url, image_factory=qrcode.image.svg.SvgPathImage, box_size=10).save("docs/qr.svg")
qrcode.make(url, box_size=10, border=4).save("docs/qr.png")
print("QR ->", url)