package kr.overbreak.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * OVERBREAK 런처 — 한 번 받아 두면 모드를 알아서 최신으로 맞춰 줍니다.
 *
 *   [업데이트 확인] 최신 update.json 을 보고 Fabric 0.19.5 · Fabric API · OVERBREAK 를 맞춥니다 (옛 판 삭제)
 *   [게임 실행]     공식 마인크래프트 런처를 띄웁니다 (로그인은 공식 런처가 그대로 씁니다)
 *   [모드 폴더]     설치된 곳을 열어 봅니다
 *
 * 전용 게임 폴더(.minecraft/overbreak)를 써서 원래 세계 · 다른 모드를 건드리지 않습니다.
 */
public final class Main {
	private static final Color BG = new Color(0x0C0E13);
	private static final Color PANEL = new Color(0x141821);
	private static final Color TEXT = new Color(0xE8EAF0);
	private static final Color MUTED = new Color(0x9AA0AC);
	private static final Color ACCENT = new Color(0xE8363C);
	private static final Color OK = new Color(0x4FD07A);

	private final Config config = Config.load();
	private final JFrame frame = new JFrame("OVERBREAK 런처");
	private final JLabel status = new JLabel("준비됨");
	private final JLabel versions = new JLabel(" ");
	private final JProgressBar bar = new JProgressBar(0, 1000);
	private final JTextArea logArea = new JTextArea();
	private final JButton updateButton = button("업데이트 확인 · 설치", true);
	private final JButton playButton = button("게임 실행", false);
	private final JButton folderButton = button("모드 폴더", false);

	private Path minecraft = Installer.findMinecraft();
	private Map<String, Object> manifest;

	public static void main(String[] args) {
		System.setProperty("sun.java2d.uiScale.enabled", "true");
		SwingUtilities.invokeLater(() -> new Main().show());
	}

	private void show() {
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setMinimumSize(new Dimension(560, 460));
		frame.getContentPane().setBackground(BG);
		frame.setLayout(new BorderLayout());

		frame.add(header(), BorderLayout.NORTH);
		frame.add(center(), BorderLayout.CENTER);
		frame.add(buttons(), BorderLayout.SOUTH);

		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		log("저장소: " + config.repo);
		refreshVersions();
		// 켜자마자 최신인지 한 번 봅니다
		new Thread(this::checkOnly, "overbreak-check").start();
	}

	// ── 화면 ────────────────────────────────────────────────

	private Component header() {
		JPanel p = new JPanel(null) {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2 = (Graphics2D) g;
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(PANEL);
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.setColor(ACCENT);
				g2.fillRect(0, getHeight() - 3, getWidth(), 3);
				g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
				g2.setColor(TEXT);
				g2.drawString("OVERBREAK", 22, 46);
				g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
				g2.setColor(MUTED);
				g2.drawString("PVP 아레나 · 자동 설치 런처", 24, 66);
			}
		};
		p.setPreferredSize(new Dimension(560, 84));
		return p;
	}

	private Component center() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(BG);
		p.setBorder(BorderFactory.createEmptyBorder(14, 20, 6, 20));

		status.setForeground(TEXT);
		status.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		versions.setForeground(MUTED);
		versions.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		versions.setAlignmentX(Component.LEFT_ALIGNMENT);

		bar.setStringPainted(false);
		bar.setForeground(ACCENT);
		bar.setBackground(PANEL);
		bar.setBorderPainted(false);
		bar.setPreferredSize(new Dimension(100, 8));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);

		logArea.setEditable(false);
		logArea.setBackground(PANEL);
		logArea.setForeground(new Color(0xB9BFC9));
		logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		logArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		JScrollPane scroll = new JScrollPane(logArea);
		scroll.setBorder(BorderFactory.createLineBorder(new Color(0x232834)));
		scroll.getViewport().setBackground(PANEL);
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroll.setPreferredSize(new Dimension(520, 200));

		p.add(status);
		p.add(Box.createVerticalStrut(4));
		p.add(versions);
		p.add(Box.createVerticalStrut(10));
		p.add(bar);
		p.add(Box.createVerticalStrut(12));
		p.add(scroll);
		return p;
	}

	private Component buttons() {
		JPanel p = new JPanel(new GridLayout(1, 3, 10, 0));
		p.setBackground(BG);
		p.setBorder(BorderFactory.createEmptyBorder(10, 20, 16, 20));
		updateButton.addActionListener(e -> run(this::installAll));
		playButton.addActionListener(e -> run(this::play));
		folderButton.addActionListener(e -> {
			if (minecraft != null) {
				Installer.openFolder(Installer.modsDir(minecraft));
			}
		});
		p.add(updateButton);
		p.add(playButton);
		p.add(folderButton);
		return p;
	}

	private JButton button(String text, boolean primary) {
		JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setOpaque(true);
		b.setBackground(primary ? ACCENT : new Color(0x222733));
		b.setForeground(primary ? Color.WHITE : TEXT);
		b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		b.setPreferredSize(new Dimension(150, 36));
		return b;
	}

	// ── 동작 ────────────────────────────────────────────────

	private void run(Runnable task) {
		setBusy(true);
		new Thread(() -> {
			try {
				task.run();
			} finally {
				SwingUtilities.invokeLater(() -> setBusy(false));
			}
		}, "overbreak-work").start();
	}

	private void setBusy(boolean busy) {
		updateButton.setEnabled(!busy);
		playButton.setEnabled(!busy);
		folderButton.setEnabled(!busy);
		if (!busy) {
			bar.setValue(0);
		}
	}

	/** 켤 때 — 최신 버전만 확인하고 알려 줍니다. */
	private void checkOnly() {
		try {
			manifest = fetchManifest();
			SwingUtilities.invokeLater(() -> {
				refreshVersions();
				String latest = Json.str(manifest, "version");
				String have = minecraft == null ? null : Installer.installedVersion(minecraft);
				if (latest != null && latest.equals(have)) {
					setStatus("최신 상태입니다", OK);
				} else {
					setStatus(have == null ? "설치가 필요합니다" : "새 버전이 있습니다 — " + latest, ACCENT);
				}
			});
		} catch (Exception e) {
			SwingUtilities.invokeLater(() -> setStatus("최신 정보를 가져오지 못했습니다", MUTED));
			log("업데이트 정보를 못 읽었습니다: " + e.getMessage());
			log("저장소가 아직 없거나 릴리스가 올라오지 않았을 수 있습니다: " + config.releasesPage());
		}
	}

	private void installAll() {
		try {
			if (minecraft == null || !Files.isDirectory(minecraft)) {
				SwingUtilities.invokeAndWait(this::askMinecraft);
			}
			if (minecraft == null) {
				log("마인크래프트 폴더를 고르지 않아 멈췄습니다.");
				return;
			}
			setStatus("업데이트 정보를 읽는 중...", TEXT);
			manifest = fetchManifest();
			String latest = Json.str(manifest, "version");
			log("최신 버전: " + latest);

			setStatus("설치 중...", TEXT);
			new Installer(this::log).install(minecraft, manifest, this::progress);
			SwingUtilities.invokeLater(() -> {
				refreshVersions();
				setStatus("설치 완료 — 게임 실행을 누르세요", OK);
			});
		} catch (Exception e) {
			log("실패: " + e.getMessage());
			SwingUtilities.invokeLater(() -> setStatus("실패 — 기록을 확인하세요", ACCENT));
		}
	}

	private Map<String, Object> fetchManifest() throws Exception {
		log("확인: " + config.manifestUrl);
		return Json.object(Net.text(config.manifestUrl));
	}

	/** 게임 실행 — 공식 런처를 찾아 띄웁니다. 못 찾으면 어디 있는지 직접 물어봅니다. */
	private void play() {
		Installer installer = new Installer(this::log);
		if (installer.openMinecraftLauncher()) {
			return;
		}
		try {
			SwingUtilities.invokeAndWait(() -> askLauncher(installer));
		} catch (Exception e) {
			log("실행 실패: " + e.getMessage());
		}
	}

	/** 마인크래프트 런처 실행 파일을 직접 고르게 합니다 (한 번 고르면 기억합니다). */
	private void askLauncher(Installer installer) {
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		String hint = os.contains("win")
				? "보통 C:\\Program Files (x86)\\Minecraft Launcher\\MinecraftLauncher.exe 에 있습니다."
				: "마인크래프트 런처 실행 파일을 골라 주세요.";
		int answer = JOptionPane.showConfirmDialog(frame,
				"마인크래프트 런처를 찾지 못했습니다.\n" + hint + "\n\n직접 골라 볼까요?",
				"마인크래프트 런처 찾기", JOptionPane.YES_NO_OPTION);
		if (answer != JOptionPane.YES_OPTION) {
			log("공식 마인크래프트 런처를 직접 켜고 「" + Installer.PROFILE_NAME + "」 프로필을 고르셔도 됩니다.");
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setDialogTitle("마인크래프트 런처 실행 파일 고르기");
		String pf = System.getenv("ProgramFiles(x86)");
		if (pf != null && Files.isDirectory(Path.of(pf))) {
			chooser.setCurrentDirectory(Path.of(pf).toFile());
		}
		if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			installer.useAndRemember(chooser.getSelectedFile().toPath());
		}
	}

	private void askMinecraft() {
		log("마인크래프트 폴더(.minecraft)를 찾지 못했습니다. 직접 골라 주세요.");
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle(".minecraft 폴더 고르기");
		if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			minecraft = chooser.getSelectedFile().toPath();
			log("마인크래프트 폴더: " + minecraft);
		}
	}

	private void progress(long got, long total) {
		int value = total > 0 ? (int) (got * 1000 / total) : (int) (got / 1024 % 1000);
		SwingUtilities.invokeLater(() -> {
			bar.setIndeterminate(total <= 0);
			bar.setValue(value);
		});
	}

	private void refreshVersions() {
		String have = minecraft == null ? null : Installer.installedVersion(minecraft);
		String latest = manifest == null ? null : Json.str(manifest, "version");
		String mc = manifest == null ? "26.2" : Json.str(manifest, "minecraft");
		String loader = manifest == null ? "0.19.5" : Json.str(manifest, "fabricLoader");
		versions.setText("설치됨 " + (have == null ? "없음" : have)
				+ "   ·   최신 " + (latest == null ? "?" : latest)
				+ "   ·   마인크래프트 " + mc + " / Fabric " + loader
				+ (minecraft == null ? "" : "   ·   " + Installer.gameDir(minecraft)));
	}

	private void setStatus(String text, Color color) {
		status.setText(text);
		status.setForeground(color);
	}

	private void log(String line) {
		SwingUtilities.invokeLater(() -> {
			logArea.append(line + "\n");
			logArea.setCaretPosition(logArea.getDocument().getLength());
		});
	}
}
