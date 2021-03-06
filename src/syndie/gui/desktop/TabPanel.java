package syndie.gui.desktop;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import syndie.data.SyndieURI;
import syndie.util.Timer;
import syndie.gui.*;

class TabPanel extends DesktopPanel {
    private Composite _browserBase;
    private Browser _browser;
    
    public TabPanel(Composite parent, Desktop desktop) {
        super(desktop, parent, desktop.getUI(), null);
        initComponents();
    }
    
    private void initComponents() {
        Composite root = getRoot();
        _browserBase = new Composite(root, SWT.NONE);
        GridLayout gl = new GridLayout(1, true);
        gl.horizontalSpacing = 0;
        gl.marginHeight = 0;
        gl.marginWidth = 0;
        gl.verticalSpacing = 0;
        _browserBase.setLayout(gl);
    }
    
    public void shown(Desktop desktop, SyndieURI uri, String suggestedName, String suggestedDescription) {
        if (_browser == null) {
            getRoot().getShell().setRedraw(false);
            getRoot().setRedraw(false);
            _browser = new Browser(_desktop.getDBClient(), getRoot().getShell(), _browserBase, _desktop.getNavControl(), _desktop.getThemeRegistry(), _desktop.getTranslationRegistry());
            _browser.addUI(_desktop.getUI());
            Timer t = new Timer("tab panel startup", _desktop.getUI());
            _browser.startup(t);
            t.addEvent("done with the nested browser startup");
            t.complete();
            getRoot().setRedraw(true);
            getRoot().getShell().setRedraw(true);
        }
        // tell the desktop to start using the tabs
        _desktop.setNavControl(_browser.getNavControl());
        ComponentBuilder.instance().setNavigationControl(_browser.getNavControl());
        if (uri != null)
            _browser.view(uri, suggestedName, suggestedDescription);
        super.shown(desktop, uri, suggestedName, suggestedDescription);
    }
    
    public void unview(SyndieURI uri) { _browser.unview(uri); }
    
    protected boolean canClose() { return false; }
    
    public String getPanelName() { return "tabs"; }
    public String getPanelDescription() { return "Old style tabbed interface"; }
}
