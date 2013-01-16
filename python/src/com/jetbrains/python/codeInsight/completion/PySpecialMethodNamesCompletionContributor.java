package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Completes predefined method names like __str__
 * User: dcheryasov
 * Date: Dec 3, 2009 10:06:12 AM
 */
public class PySpecialMethodNamesCompletionContributor extends CompletionContributor {
  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
    // auto-insert the obvious only case; else show other cases. 
    final LookupElement[] items = context.getItems();
    if (items.length == 1) {
      return AutoCompletionDecision.insertItem(items[0]);
    }
    return AutoCompletionDecision.SHOW_LOOKUP;
  }

  public PySpecialMethodNamesCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(psiElement().inside(psiElement(PyFunction.class).inside(psiElement(PyClass.class))))
        .and(psiElement().afterLeaf("def"))
     ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          for (Map.Entry<String, PyNames.BuiltinDescription> entry: PyNames.BuiltinMethods.entrySet()) {
            LookupElementBuilder item;
            item = LookupElementBuilder
              .create(entry.getKey() + entry.getValue().getSignature())
              .bold()
              .withTypeText("predefined")
              .withIcon(PythonIcons.Python.Nodes.Cyan_dot)
            ;
            result.addElement(TailTypeDecorator.withTail(item, TailType.CASE_COLON));
          }
        }
      }
    );
  }
}
