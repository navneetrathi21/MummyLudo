import AppKit
import Foundation

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let out = root.appendingPathComponent("play-store-assets", isDirectory: true)
try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)

let ink = NSColor(calibratedRed: 0x26/255, green: 0x35/255, blue: 0x2F/255, alpha: 1)
let cream = NSColor(calibratedRed: 0xF7/255, green: 0xF3/255, blue: 0xEA/255, alpha: 1)
let red = NSColor(calibratedRed: 0xEF/255, green: 0x5B/255, blue: 0x67/255, alpha: 1)
let green = NSColor(calibratedRed: 0x49/255, green: 0xB6/255, blue: 0x75/255, alpha: 1)
let yellow = NSColor(calibratedRed: 0xF6/255, green: 0xCB/255, blue: 0x3D/255, alpha: 1)
let blue = NSColor(calibratedRed: 0x43/255, green: 0x8F/255, blue: 0xE7/255, alpha: 1)

func save(_ image: NSImage, _ name: String) throws {
    guard let tiff = image.tiffRepresentation,
          let bitmap = NSBitmapImageRep(data: tiff),
          let png = bitmap.representation(using: .png, properties: [:]) else {
        throw NSError(domain: "asset", code: 1)
    }
    try png.write(to: out.appendingPathComponent(name))
}

func drawImage(width: Int, height: Int, _ draw: (NSRect) -> Void) -> NSImage {
    let image = NSImage(size: NSSize(width: width, height: height))
    image.lockFocus()
    draw(NSRect(x: 0, y: 0, width: width, height: height))
    image.unlockFocus()
    return image
}

func roundedRect(_ rect: NSRect, radius: CGFloat, color: NSColor) {
    color.setFill()
    NSBezierPath(roundedRect: rect, xRadius: radius, yRadius: radius).fill()
}

func drawBoardIcon(in rect: NSRect) {
    roundedRect(rect, radius: rect.width * 0.18, color: .white)
    let gap = rect.width * 0.055
    let half = (rect.width - gap * 3) / 2
    let x0 = rect.minX + gap
    let y0 = rect.minY + gap
    let x1 = x0 + half + gap
    let y1 = y0 + half + gap
    roundedRect(NSRect(x: x0, y: y1, width: half, height: half), radius: half * 0.08, color: red)
    roundedRect(NSRect(x: x1, y: y1, width: half, height: half), radius: half * 0.08, color: green)
    roundedRect(NSRect(x: x0, y: y0, width: half, height: half), radius: half * 0.08, color: blue)
    roundedRect(NSRect(x: x1, y: y0, width: half, height: half), radius: half * 0.08, color: yellow)
    roundedRect(NSRect(x: rect.midX - rect.width * 0.16, y: rect.midY - rect.width * 0.16, width: rect.width * 0.32, height: rect.width * 0.32), radius: rect.width * 0.07, color: cream)
    ink.setFill()
    for point in [NSPoint(x: rect.midX - rect.width * 0.055, y: rect.midY + rect.width * 0.055), NSPoint(x: rect.midX + rect.width * 0.055, y: rect.midY - rect.width * 0.055)] {
        NSBezierPath(ovalIn: NSRect(x: point.x - rect.width * 0.025, y: point.y - rect.width * 0.025, width: rect.width * 0.05, height: rect.width * 0.05)).fill()
    }
}

let icon = drawImage(width: 512, height: 512) { rect in
    cream.setFill()
    rect.fill()
    drawBoardIcon(in: rect.insetBy(dx: 54, dy: 54))
}
try save(icon, "icon-512.png")

let feature = drawImage(width: 1024, height: 500) { rect in
    cream.setFill()
    rect.fill()
    let stripeHeight: CGFloat = 18
    red.setFill(); NSRect(x: 0, y: rect.height - stripeHeight, width: rect.width * 0.25, height: stripeHeight).fill()
    green.setFill(); NSRect(x: rect.width * 0.25, y: rect.height - stripeHeight, width: rect.width * 0.25, height: stripeHeight).fill()
    yellow.setFill(); NSRect(x: rect.width * 0.5, y: rect.height - stripeHeight, width: rect.width * 0.25, height: stripeHeight).fill()
    blue.setFill(); NSRect(x: rect.width * 0.75, y: rect.height - stripeHeight, width: rect.width * 0.25, height: stripeHeight).fill()

    drawBoardIcon(in: NSRect(x: 632, y: 74, width: 320, height: 320))

    let titleAttrs: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: 76, weight: .bold),
        .foregroundColor: ink
    ]
    let subtitleAttrs: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: 30, weight: .semibold),
        .foregroundColor: NSColor(calibratedRed: 0x3E/255, green: 0x66/255, blue: 0x55/255, alpha: 1)
    ]
    "Mummy Ludo".draw(at: NSPoint(x: 72, y: 278), withAttributes: titleAttrs)
    "Classic local Ludo with fair bots".draw(at: NSPoint(x: 78, y: 226), withAttributes: subtitleAttrs)
    "Solo · 2P · 3P · 4P".draw(at: NSPoint(x: 78, y: 178), withAttributes: [
        .font: NSFont.systemFont(ofSize: 25, weight: .medium),
        .foregroundColor: ink.withAlphaComponent(0.72)
    ])
}
try save(feature, "feature-graphic-1024x500.png")

let readme = """
# Play Store Assets

Generated assets for the Mummy Ludo Play Store listing.

## Required graphics

- `icon-512.png` — 512 x 512 app icon.
- `feature-graphic-1024x500.png` — 1024 x 500 feature graphic.
- `phone-screenshots/` — 1080 x 2400 phone screenshots.

## Notes

Use the phone screenshots in order for Internal testing or the store listing. The Play Console may ask for at least two phone screenshots; this folder includes seven.
"""
try readme.write(to: out.appendingPathComponent("README.md"), atomically: true, encoding: .utf8)

