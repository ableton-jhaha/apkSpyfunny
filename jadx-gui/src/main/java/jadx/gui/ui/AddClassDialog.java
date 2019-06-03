package jadx.gui.ui;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.apache.commons.lang3.StringUtils;

import com.lucasbaizer.ApkSpy;
import com.lucasbaizer.ChangeCache;
import com.lucasbaizer.ClassBreakdown;

import jadx.api.JavaClass;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.nodes.ClassNode;
import jadx.gui.treemodel.JClass;
import jadx.gui.treemodel.JNode;
import jadx.gui.treemodel.JPackage;
import jadx.gui.utils.JumpPosition;

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
		ClassBreakdown breakdown = ClassBreakdown.breakdown(null, null, this.getCodeArea().getText());
		breakdown.setFullName(this.packageName + "." + breakdown.getSimpleName());

		JClass cls = new JClass(new JavaClass(
				new ClassNode(this.getCodeArea().getText().trim(),
						ClassInfo.fromEditor(breakdown.getSimpleName(), breakdown.getFullName(), this.packageName)),
				null));
		cls.setUserObject(breakdown.getSimpleName());
		cls.setLoaded(true);

		this.packageNode.getClasses().add(cls);
		Collections.sort(this.packageNode.getClasses());
		this.packageNode.update();

		DefaultTreeModel model = (DefaultTreeModel) this.tree.getModel();
		model.reload(this.packageNode);

		this.tree.setSelectionPath(new TreePath(model.getPathToRoot(cls)));

		this.getMainWindow().getTabbedPane().codeJump(new JumpPosition(cls, 0));

		ChangeCache.putChange(breakdown.getFullName(), breakdown, null);

		dispose();
	}

	@Override
	protected void onCompile() {
		ClassBreakdown breakdown = ClassBreakdown.breakdown(null, null, this.getCodeArea().getText());
		breakdown.setFullName(this.packageName + "." + breakdown.getSimpleName());

		try {
			if (ApkSpy.lint(this.getMainWindow().getProject().getFilePath().toString(), breakdown.getFullName(),
					breakdown, new OutputStream() {
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
