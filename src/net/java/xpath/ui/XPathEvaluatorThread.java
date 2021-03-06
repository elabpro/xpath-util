package net.java.xpath.ui;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import org.openide.util.Exceptions;
import org.xml.sax.SAXException;

final class XPathEvaluatorThread extends Thread {

    
    private volatile boolean updated = false;

    private final JTextComponent component;
    private final Runnable edtRunner;

    private final XPathEvaluator eval;

    private String xpath;
    private String xml;
    private String result;

    XPathEvaluatorThread(JTextComponent component) {

        super("XPath-evaluator");

        this.component = component;
        this.setDaemon(true);

        this.eval = new XPathEvaluator();

        this.edtRunner = new Runnable() {
            @Override
            public void run() {
                XPathEvaluatorThread.this.component.setText(result);
            }
        };


    }

    @Override
    public void run() {

        while (!Thread.interrupted()) {

            // don't sleep if values have changed
            if (!updated) {
                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try {
                updated = false;
                result = eval.evalXPathToString(xpath, xml);
            } catch (SAXException ex) {
                result = "unable to parse document";
                ex.printStackTrace();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } catch (TransformerException ex) {
                Exceptions.printStackTrace(ex);
            } catch (XPathExpressionException ex) {
                // return localized exception message on illegal xpath expr.
                result = ex.getCause().getLocalizedMessage();
            }
            /*
             * don't update if dirty (racecondition possible but doeasn't matter)
             */
            if (updated && (result == null || result.isEmpty())) {
                continue;
            }
            try {
                SwingUtilities.invokeAndWait(edtRunner);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (InvocationTargetException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }



    /**
     * Starts asynchronous evaluation of the xpath expression. This method
     * can be called concurrently even if a evaluation is in progress.
     */
    synchronized void asyncEval(String xpath, String xml) {
        this.updated = true;
        this.xml = xml;
        this.xpath = xpath;
        if (isAlive()) {
            notify();
        } else {
            start();
        }
    }
}
