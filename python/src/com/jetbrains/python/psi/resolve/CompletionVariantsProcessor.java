package com.jetbrains.python.psi.resolve;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.completion.PyClassInsertHandler;
import com.jetbrains.python.codeInsight.completion.PyFunctionInsertHandler;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public class CompletionVariantsProcessor extends VariantsProcessor {
  private final Map<String, LookupElement> myVariants = new HashMap<String, LookupElement>();
  private boolean mySuppressParentheses = false;

  public CompletionVariantsProcessor(PsiElement context) {
    super(context);
  }

  public CompletionVariantsProcessor(PsiElement context,
                                     @Nullable Condition<PsiElement> nodeFilter,
                                     @Nullable Condition<String> nameFilter) {
    super(context, nodeFilter, nameFilter);
  }

  public void suppressParentheses() {
    mySuppressParentheses = true;
  }

  protected LookupElementBuilder setupItem(LookupElementBuilder item) {
    final Object object = item.getObject();
    if (!myPlainNamesOnly) {
      if (!mySuppressParentheses &&
          object instanceof PyFunction && ((PyFunction)object).getProperty() == null &&
          hasNoCustomDecorators((PyFunction)object) &&
          !isSingleArgDecoratorCall(myContext, (PyFunction)object)) {
        item = item.withInsertHandler(PyFunctionInsertHandler.INSTANCE);
        final PyParameterList parameterList = ((PyFunction)object).getParameterList();
        final String params = StringUtil.join(parameterList.getParameters(), new Function<PyParameter, String>() {
          @Override
          public String fun(PyParameter pyParameter) {
            return pyParameter.getName();
          }
        }, ", ");
        item = item.withTailText("(" + params + ")");
      }
      else if (object instanceof PyClass) {
        item = item.withInsertHandler(PyClassInsertHandler.INSTANCE);
      }
    }
    String source = null;
    if (object instanceof PsiElement) {
      final PsiElement element = (PsiElement)object;
      PyClass cls = null;

      if (element instanceof PyFunction) {
        cls = ((PyFunction)element).getContainingClass();
      }
      else if (element instanceof PyTargetExpression) {
        final PyTargetExpression expr = (PyTargetExpression)element;
        if (expr.isQualified() || ScopeUtil.getScopeOwner(expr) instanceof PyClass) {
          cls = expr.getContainingClass();
        }
      }
      else if (element instanceof PyClass) {
        final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
        if (owner instanceof PyClass) {
          cls = (PyClass)owner;
        }
      }

      if (cls != null) {
        source = cls.getName();
      }
      else if (myContext == null || !PyUtil.inSameFile(myContext, element)) {
        PyQualifiedName path = QualifiedNameFinder.findCanonicalImportPath(element, null);
        if (path != null) {
          if (element instanceof PyFile) {
            path = path.removeLastComponent();
          }
          source = path.toString();
        }
      }
    }
    if (source != null) {
      item = item.withTypeText(source);
    }
    return item;
  }

  private static boolean hasNoCustomDecorators(PyFunction function) {
    PyDecoratorList decoratorList = function.getDecoratorList();
    if (decoratorList == null) {
      return true;
    }
    for (PyDecorator decorator : decoratorList.getDecorators()) {
      PyQualifiedName name = decorator.getQualifiedName();
      if (name == null || (!PyNames.CLASSMETHOD.equals(name.toString()) && !PyNames.STATICMETHOD.equals(name.toString()))) {
        return false;
      }
    }

    return true;
  }

  private static boolean isSingleArgDecoratorCall(PsiElement elementInCall, PyFunction callee) {
    // special case hack to avoid the need of patching generator3.py
    PyClass containingClass = callee.getContainingClass();
    if (containingClass != null && PyNames.PROPERTY.equals(containingClass.getName()) &&
        PyBuiltinCache.getInstance(elementInCall).hasInBuiltins(containingClass)) {
      return true;
    }

    if (callee.getParameterList().getParameters().length > 1) {
      return false;
    }
    PyDecorator decorator = PsiTreeUtil.getParentOfType(elementInCall, PyDecorator.class);
    if (decorator == null) {
      return false;
    }
    return PsiTreeUtil.isAncestor(decorator.getCallee(), elementInCall, false);
  }

  protected static LookupElementBuilder setItemNotice(final LookupElementBuilder item, String notice) {
    return item.withTypeText(notice);
  }

  public LookupElement[] getResult() {
    final Collection<LookupElement> variants = myVariants.values();
    return variants.toArray(new LookupElement[variants.size()]);
  }

  public List<LookupElement> getResultList() {
    return new ArrayList<LookupElement>(myVariants.values());
  }

  @Override
  protected void addElement(String name, PsiElement element) {
    myVariants.put(name, setupItem(LookupElementBuilder.create(element, name).withIcon(element.getIcon(0))));
  }

  protected void addImportedElement(String referencedName, NameDefiner definer, PyElement expr) {
    Icon icon = expr.getIcon(0);
    // things like PyTargetExpression cannot have a general icon, but here we only have variables
    if (icon == null) icon = PlatformIcons.VARIABLE_ICON;
    LookupElementBuilder lookupItem = setupItem(LookupElementBuilder.create(expr, referencedName).withIcon(icon));
    myVariants.put(referencedName, lookupItem);
  }
}
