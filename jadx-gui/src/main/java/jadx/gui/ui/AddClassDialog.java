package jadx.gui.ui;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.lang3.StringUtils;

import com.lucasbaizer.ApkSpy;
import com.lucasbaizer.ClassBreakdown;

import jadx.gui.treemodel.JNode;

public class AddClassDialog extends ApkSpyDialog {
	private static final long serialVersionUID = -8177172816914615698L;

	private String packageName;

	public AddClassDialog(MainWindow mainWindow, JNode jnode, String defaultText, String packageName) {
		super(mainWindow, jnode, "Add Class");

		this.packageName = packageName;

		this.getCodeArea().setText(defaultText);
		this.getCodeArea().setCaretPosition(StringUtils.ordinalIndexOf(defaultText, "\n", 3) + 5);
	}

	@Override
	protected void onSave() {
		dispose();
	}

	@Override
	protected void onCompile() {
		ClassBreakdown breakdown = ClassBreakdown.breakdown(null, this.getCodeArea().getText());

		try {
			if (ApkSpy.lint(this.getMainWindow().getProject().getFilePath().toString(),
					this.packageName + "." + breakdown.getClassName(), breakdown, new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							System.out.print((char) b);
							getOutput().append(Character.toString((char) b));
						}
					})) {
				this.getOutput().append("Successfully compiled!\n");
			} else {
				this.getOutput().append("Encounted errors while compiling!\n");
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}
