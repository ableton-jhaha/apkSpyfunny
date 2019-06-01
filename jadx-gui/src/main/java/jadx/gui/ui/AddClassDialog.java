package jadx.gui.ui;

import java.io.IOException;
import java.io.OutputStream;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

import org.apache.commons.lang3.StringUtils;

import com.lucasbaizer.ApkSpy;
import com.lucasbaizer.ClassBreakdown;

import jadx.api.JavaClass;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.nodes.ClassNode;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.treemodel.JPackage;

public class AddClassDialog extends ApkSpyDialog {
	private static final long serialVersionUID = -8177172816914615698L;

	private String packageName;
	private JPackage packageNode;
	private JTree tree;

	public AddClassDialog(MainWindow mainWindow, JNode jnode, String defaultText, String packageName,
			JPackage packageNode, JTree tree) {
		super(mainWindow, jnode, "Add Class");

		this.packageName = packageName;
		this.packageNode = packageNode;
		this.tree = tree;

		this.getCodeArea().setText(defaultText);
		this.getCodeArea().setCaretPosition(StringUtils.ordinalIndexOf(defaultText, "\n", 3) + 5);
	}

	@Override
	protected void onSave() {
		ClassBreakdown breakdown = ClassBreakdown.breakdown(null, this.getCodeArea().getText());

		JClass cls = new JClass(
				new JavaClass(new ClassNode(this.getCodeArea().getText(), ClassInfo.fromEditor(breakdown.getClassName(),
						this.packageName + "." + breakdown.getClassName(), this.packageName)), null));
		cls.setLoaded(true);

		this.packageNode.getClasses().add(cls);
		this.packageNode.update();

		((DefaultTreeModel) this.tree.getModel()).reload();

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
