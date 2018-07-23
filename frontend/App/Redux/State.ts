import { UserState } from "./UserRedux";
import { SearchState } from "./SearchRedux";
import { ListState } from "./ListRedux";
import { NavigationState } from "./NavRedux";
import { EventsState } from "./EventsRedux";

export interface State {
    user: UserState,
    search: SearchState,
    lists: ListState,
    nav: NavigationState,
    events: EventsState
};