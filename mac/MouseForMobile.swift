import AppKit
import Foundation
import Darwin

func transmit(host: String, port: String, message: String) -> Bool {
    var hints = addrinfo(); hints.ai_family = AF_INET; hints.ai_socktype = SOCK_STREAM; hints.ai_protocol = IPPROTO_TCP
    var addresses: UnsafeMutablePointer<addrinfo>?
    guard getaddrinfo(host, port, &hints, &addresses) == 0, let address = addresses else { return false }
    defer { freeaddrinfo(addresses) }
    let fd = socket(address.pointee.ai_family, SOCK_STREAM, 0)
    guard fd >= 0 else { return false }; defer { close(fd) }
    var timeout = timeval(tv_sec: 1, tv_usec: 0)
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, socklen_t(MemoryLayout<timeval>.size))
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, socklen_t(MemoryLayout<timeval>.size))
    var yes: Int32 = 1; setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &yes, socklen_t(MemoryLayout<Int32>.size))
    // Nonblocking connect keeps an unreachable phone from hanging the worker.
    let flags = fcntl(fd, F_GETFL); _ = fcntl(fd, F_SETFL, flags | O_NONBLOCK)
    let connected = connect(fd, address.pointee.ai_addr, address.pointee.ai_addrlen)
    if connected != 0 {
        guard errno == EINPROGRESS else { return false }
        var pollFD = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
        guard poll(&pollFD, 1, 1000) > 0 else { return false }
        var error: Int32 = 0; var length = socklen_t(MemoryLayout<Int32>.size)
        guard getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &length) == 0 && error == 0 else { return false }
    }
    _ = fcntl(fd, F_SETFL, flags)
    let bytes = Array((message + "\n").utf8)
    let count = bytes.withUnsafeBytes { send(fd, $0.baseAddress, $0.count, 0) }
    guard count == bytes.count else { return false }
    var reply = [UInt8](repeating: 0, count: 32)
    let n = recv(fd, &reply, reply.count, 0)
    return n >= 3 && String(bytes: reply.prefix(n), encoding: .utf8)?.hasPrefix("OK\n") == true
}

class Controller: NSObject, NSApplicationDelegate, NSWindowDelegate {
    var window: NSWindow!
    let address = NSTextField(string: "")
    let code = NSSecureTextField(string: "")
    let status = NSTextField(labelWithString: "Enter the address and pairing code shown on your phone.")
    var captured = false, busy = false
    var dx: Double = 0, dy: Double = 0
    var actions: [String] = []
    var activeHost = "", activePort = "8765", activeCode = ""
    var timer: Timer?
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        window = NSWindow(contentRect: NSRect(x: 0,y: 0,width: 600,height: 350), styleMask: [.titled,.closable,.miniaturizable], backing: .buffered, defer: false)
        window.title = "Mouse for Mobile"; window.delegate = self; window.acceptsMouseMovedEvents = true
        let stack = NSStackView(); stack.orientation = .vertical; stack.alignment = .leading; stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        window.contentView!.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo: window.contentView!.leadingAnchor, constant: 24),stack.trailingAnchor.constraint(equalTo: window.contentView!.trailingAnchor, constant: -24),stack.topAnchor.constraint(equalTo: window.contentView!.topAnchor, constant: 24)])
        address.placeholderString = "Phone address, e.g. 192.168.1.25:8765"; code.placeholderString = "Pairing code"
        let button = NSButton(title: "Control phone", target: self, action: #selector(capture))
        let help = NSTextField(wrappingLabelWithString: "Click Control phone, then use your mouse while watching the phone.\nClick = tap · Right click = Back · Scroll = swipe\nH = Home · Space = long press · Escape = release mouse\n\nKeep this window active. Both devices must use the same trusted Wi-Fi.")
        for v in [address,code,button,help,status] { stack.addArrangedSubview(v) }
        address.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true
        code.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true
        status.font = .systemFont(ofSize: 12)
        NSEvent.addLocalMonitorForEvents(matching: [.mouseMoved,.leftMouseDragged,.rightMouseDragged,.leftMouseDown,.rightMouseDown,.leftMouseUp,.rightMouseUp,.scrollWheel,.keyDown,.keyUp]) { [weak self] event in
            guard let self = self, self.captured else { return event }
            switch event.type {
            case .mouseMoved,.leftMouseDragged,.rightMouseDragged: self.dx += event.deltaX * 1.5; self.dy += event.deltaY * 1.5
            case .leftMouseDown: self.enqueue("tap")
            case .rightMouseDown: self.enqueue("back")
            case .scrollWheel: if abs(event.scrollingDeltaY) > 0 { self.enqueue("scroll \(event.scrollingDeltaY * 18)") }
            case .keyDown:
                if event.keyCode == 53 { self.releaseMouse() }
                else if event.keyCode == 49 { self.enqueue("hold") }
                else if event.charactersIgnoringModifiers?.lowercased() == "h" { self.enqueue("home") }
            default: break
            }
            return nil
        }
        timer = Timer.scheduledTimer(withTimeInterval: 1.0/30, repeats: true) { [weak self] _ in self?.flush() }
        window.center(); window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true)
    }
    @objc func capture() {
        let parts = address.stringValue.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: ":")
        guard parts.count == 1 || parts.count == 2, !code.stringValue.isEmpty else { status.stringValue = "Enter a phone address and pairing code first."; return }
        activeHost = String(parts[0]); activePort = parts.count == 2 ? String(parts[1]) : "8765"; activeCode = code.stringValue.trimmingCharacters(in: .whitespacesAndNewlines)
        captured = true; dx = 0; dy = 0; actions = ["ping"]
        NSCursor.hide(); CGAssociateMouseAndMouseCursorPosition(0)
        status.stringValue = "Connecting… Press Escape to release the mouse."
    }
    func enqueue(_ command: String) {
        if dx != 0 || dy != 0 { actions.append("move \(dx) \(dy)"); dx = 0; dy = 0 }
        if actions.count < 12 { actions.append(command) }
    }
    func flush() {
        guard captured && !busy else { return }
        if actions.isEmpty && (dx != 0 || dy != 0) { actions.append("move \(dx) \(dy)"); dx = 0; dy = 0 }
        guard !actions.isEmpty else { return }
        let command = actions.removeFirst(), host = activeHost, port = activePort, token = activeCode
        busy = true
        DispatchQueue.global(qos: .userInteractive).async {
            let ok = transmit(host: host, port: port, message: token + " " + command)
            DispatchQueue.main.async {
                self.busy = false
                guard self.captured else { return }
                if ok { self.status.stringValue = "Controlling phone · Escape releases the mouse" }
                else { self.releaseMouse(); self.status.stringValue = "Connection failed. Check Wi-Fi, sharing, address and pairing code." }
            }
        }
    }
    func releaseMouse() {
        if captured { captured = false; CGAssociateMouseAndMouseCursorPosition(1); NSCursor.unhide() }
        actions.removeAll(); dx = 0; dy = 0; status.stringValue = "Mouse released."
    }
    func windowDidResignKey(_ notification: Notification) { releaseMouse() }
    func applicationWillTerminate(_ notification: Notification) { releaseMouse() }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
}
let app = NSApplication.shared
let controller = Controller()
app.delegate = controller
app.run()
