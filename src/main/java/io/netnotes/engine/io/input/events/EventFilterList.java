package io.netnotes.engine.io.input.events;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Predicate;

public class EventFilterList implements Predicate<RoutedEvent> {

    protected FilterMode mode = FilterMode.ANY;

    protected boolean enableOnAdd = true;
    protected boolean enabled = false;
  
    protected final ArrayList<Predicate<RoutedEvent>> filters = new ArrayList<>();

    public enum FilterMode{
        /** All filters must accept the event (logical AND) */
        ALL,

        /** At least one filter must accept the event (logical OR) */
        ANY,

        /** No filters may accept the event (logical NOR) */
        NONE,

        /** Exactly one filter must accept the event */
        ONE;
    }

    public boolean isEnabled(){
        return enabled;
    }

    @Override
    public boolean test(RoutedEvent event) {
        if(filters.isEmpty() || !enabled) return true;

        switch (mode) {
            case ALL:
                for (Predicate<RoutedEvent> f : filters) {
                    if (!f.test(event)) return false;
                }
                return true;

            case ANY:
                for (Predicate<RoutedEvent>  f : filters) {
                    if (f.test(event)) return true;
                }
                return false;

            case NONE:
                for (Predicate<RoutedEvent>  f : filters) {
                    if (f.test(event)) return false;
                }
                return true;

            case ONE:
                boolean matched = false;
                for (Predicate<RoutedEvent>  f : filters) {
                    if (f.test(event)) {
                        if (matched) return false;
                        matched = true;
                    }
                }
                return matched;

            default:
                return true;
        }

    }

    public boolean isEnableOnAdd(){ return enableOnAdd; }

    public void setEnableOnAdd(boolean enableOnAdd) { this.enableOnAdd = enableOnAdd; }

    public void setEnabled(boolean enabled){ this.enabled = enabled; }

    public boolean removeEventFilterById(String id) {
        EventFilter  filter = getEventFilterById(id);

        if(filter != null){
            return removePredicate(filter);
        }

        return false;
    }

    public EventFilter  getEventFilterById(String id){
        for (Predicate<RoutedEvent>  filter : filters) {
            if (filter instanceof EventFilter f && f.getId().equals(id)) {
                return f;
            }
        }
        return null;
    }

    public boolean addPredicate(Predicate<RoutedEvent>  filter) {
        if(enableOnAdd){
            enabled = true;
        }
        return filters.add(Objects.requireNonNull(filter));
    }

    public boolean addPredicateIfNotExists(EventFilter filter) {
        if(filter == null){
            return false;
        }
        if(enableOnAdd){
            enabled = true;
        }
        EventFilter existing = getEventFilterById(filter.getId());
        if(existing == null){
            return filters.add(filter);
        }else{
            return true;
        }
    }

    public boolean removePredicate(Predicate<RoutedEvent>  filter) {
        boolean removed = filters.remove(filter);
        if(enableOnAdd){
            if(filters.isEmpty()){
                enabled = false;
            }
        }
        return removed;
    }

   
    public boolean isEmpty() {
        return filters.isEmpty();
    }

    public void setMode(FilterMode mode) {
        this.mode = Objects.requireNonNull(mode);
    }

    public FilterMode getMode() {
        return mode;
    }

    public void clear(){
        filters.clear();
        if(enableOnAdd){
            enabled = false;
        }
    }
}
