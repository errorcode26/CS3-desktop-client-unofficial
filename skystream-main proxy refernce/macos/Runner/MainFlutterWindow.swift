import Cocoa
import FlutterMacOS

class MainFlutterWindow: NSWindow {
  override func awakeFromNib() {
    let flutterViewController = FlutterViewController()
    let windowFrame = self.frame
    self.contentViewController = flutterViewController
    self.setFrame(windowFrame, display: true)

    // Enforce a sensible minimum content size so the user can't drag the
    // window down to a zero-pixel slit (audit H12). 800×600 covers the
    // smallest layout we ship — sidebar + content row stays usable.
    self.contentMinSize = NSSize(width: 800, height: 600)

    RegisterGeneratedPlugins(registry: flutterViewController)

    super.awakeFromNib()
  }
}
