package jadx.gui.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.DefaultCaret;

import com.lucasbaizer.ApkSpy;
import com.lucasbaizer.ChangeCache;
import com.lucasbaizer.Util;

public class ApkSpySaver extends JDialog {
	private static final long serialVersionUID = -2062775409291032261L;

	public ApkSpySaver(MainWindow mainWindow) {
		JPanel panel = new JPanel();

		JTextArea output = new JTextArea();
		output.setEditable(false);

		final JTextField saveLocation = new JTextField(30);
		saveLocation.setText(mainWindow.getProject().getFilePath().toString());

		final JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		final JButton generate = new JButton("Save");
		generate.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					if (!Util.isValidSdkPath(Paths.get(mainWindow.getSettings().getAndroidSdkPath()))) {
						JOptionPane.showMessageDialog(mainWindow,
								"Please set a valid Android SDK path in the apkSpy -> Preferences menu.", "apkSpy",
								JOptionPane.ERROR_MESSAGE);
						return;
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}

				if (ChangeCache.getChanges().size() == 0) {
					JOptionPane.showMessageDialog(mainWindow, "No changes have been made!", "apkSpy",
							JOptionPane.ERROR_MESSAGE);
					return;
				}

				cancel.setEnabled(false);
				generate.setEnabled(false);

				output.setText("");

				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							boolean success = ApkSpy.merge(mainWindow.getProject().getFilePath().toString(),
									saveLocation.getText(), mainWindow.getSettings().getAndroidSdkPath(), "jadx",
									ChangeCache.getChanges(), new OutputStream() {
										@Override
										public void write(int b) throws IOException {
											System.out.print((char) b);
											output.append(Character.toString((char) b));
										}
									});
							if (success) {
								JOptionPane.showMessageDialog(mainWindow, "Successfully created APK!", "apkSpy",
										JOptionPane.INFORMATION_MESSAGE);
								dispose();
							} else {
								cancel.setEnabled(true);
								generate.setEnabled(true);
							}
						} catch (IOException | InterruptedException e) {
							e.printStackTrace();
						}
					}
				});
				thread.start();
			}
		});

		DefaultCaret caret = (DefaultCaret) output.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		JScrollPane scroll = new JScrollPane(output);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.setPreferredSize(new Dimension(800, 600));

		panel.add(scroll);

		JPanel buttons = new JPanel();
		buttons.add(new JLabel("Save As: "));
		buttons.add(saveLocation);
		buttons.add(generate);
		buttons.add(cancel);

		add(buttons, BorderLayout.PAGE_START);
		add(panel, BorderLayout.PAGE_END);

		output.setFont(new Font("Courier New", Font.PLAIN, output.getFont().getSize()));

		setTitle("Save APK");
		pack();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.MODELESS);
		setLocationRelativeTo(null);

		generate.requestFocus();
	}
}
