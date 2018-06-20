import { UserState } from "./UserRedux";
import { SearchState } from "./SearchRedux";

export default interface State {
    user: UserState,
    search: SearchState
};