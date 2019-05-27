package jadx.gui.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.lucasbaizer.Util;

public class ApkSpyConfigurator extends JDialog {
	private static final long serialVersionUID = -2062775409291032261L;

	public ApkSpyConfigurator(MainWindow mainWindow) {
		JPanel panel = new JPanel();
		panel.add(new JLabel("Android SDK Path: "));

		JTextField sdkPath = new JTextField(30);
		sdkPath.setText(mainWindow.getSettings().getAndroidSdkPath());
		panel.add(sdkPath);

		add(panel, BorderLayout.PAGE_START);

		JPanel buttons = new JPanel();

		JButton save = new JButton("Save");
		save.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Path path = Paths.get(sdkPath.getText());
				try {
					if (!Util.isValidSdkPath(path)) {
						JOptionPane.showMessageDialog(mainWindow, "Invalid SDK path!", "apkSpy",
								JOptionPane.ERROR_MESSAGE);
					} else {
						mainWindow.getSettings().setAndroidSdkPath(path.toAbsolutePath().toString());
						mainWindow.getSettings().sync();
						dispose();
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});

		buttons.add(save);
		buttons.add(cancel);

		add(buttons, BorderLayout.PAGE_END);

		setTitle("apkSpy");
		pack();
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.MODELESS);
		setLocationRelativeTo(null);
	}
}
