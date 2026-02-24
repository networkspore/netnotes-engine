package io.netnotes.engine.ui.renderer.layout;

import java.util.Objects;

@FunctionalInterface
public interface GroupCallbackPredicate<G> {
   boolean shouldExecute(G group, String callbackId);

   default GroupCallbackPredicate<G> and(GroupCallbackPredicate<? super G> other) {
      Objects.requireNonNull(other);
      return (t, callbackId) -> {
         return this.shouldExecute(t, callbackId) && other.shouldExecute(t, callbackId);
      };
   }

   default GroupCallbackPredicate<G> negate() {
      return (t, callbackId) -> {
         return !this.shouldExecute(t, callbackId);
      };
   }

   default GroupCallbackPredicate<G> or(GroupCallbackPredicate<? super G> other) {
      Objects.requireNonNull(other);
      return (t, callbackId) -> {
         return this.shouldExecute(t, callbackId) || other.shouldExecute(t, callbackId);
      };
   }

   static <G> GroupCallbackPredicate<G> isEqual(Object targetRef) {
      return null == targetRef ? (object, callbackId) -> object == null : (object, callbackId) -> {
         return targetRef.equals(object);
      };
   }

   static <G> GroupCallbackPredicate<? super G> not(GroupCallbackPredicate<? super G> target) {
      Objects.requireNonNull(target);
      return target.negate();
   }
}
