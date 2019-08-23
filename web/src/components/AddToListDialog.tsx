import {
  Button,
  Checkbox,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormHelperText,
  IconButton,
  Input,
  InputAdornment,
  InputLabel,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { ChangeEvent, Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import {
  addToList,
  createList,
  LIST_ADD_ITEM_INITIATED,
  ListUpdate,
  ListUpdatedInitiatedPayload,
  USER_SELF_CREATE_LIST,
  UserCreateListPayload,
} from '../actions/lists';
import { AppState } from '../reducers';
import { ListOperationState, ListsByIdMap } from '../reducers/lists';
import { List, Thing } from '../types';
import _ from 'lodash';
import { UserSelf } from '../reducers/user';
import { Cancel, Check } from '@material-ui/icons';
import CreateAListValidator, {
  CreateAListValidationStateObj,
} from '../utils/validation/CreateAListValidator';

const styles = (theme: Theme) =>
  createStyles({
    formControl: {
      margin: theme.spacing(3),
    },
  });

interface AddToListDialogProps {
  open: boolean;
  onClose: () => void;
  userSelf: UserSelf;
  item: Thing;
  listOperations: ListOperationState;
  listItemAddLoading: boolean;
  createAListLoading: boolean;
  listsById: ListsByIdMap;
}

interface AddToListDialogDispatchProps {
  addToList: (listId: string, itemId: string) => void;
  updateLists: (payload: ListUpdatedInitiatedPayload) => void;
  createList: (payload: UserCreateListPayload) => void;
}

interface AddToListDialogState {
  exited: boolean;
  actionPending: boolean;
  originalListState: { [listId: number]: boolean };
  listChanges: { [listId: number]: boolean };
  createAListEnabled: boolean;
  newListName: string;
  newListValidation: CreateAListValidationStateObj;
}

type Props = AddToListDialogProps &
  AddToListDialogDispatchProps &
  WithStyles<typeof styles>;

class AddToListDialog extends Component<Props, AddToListDialogState> {
  constructor(props: Props) {
    super(props);

    let listChanges = R.reduce(
      (acc, elem) => {
        return {
          ...acc,
          [elem.id]: this.listContainsItem(elem, props.item),
        };
      },
      {},
      R.values(props.listsById),
    );

    this.state = {
      exited: false,
      actionPending: props.listOperations.inProgress,
      originalListState: {
        ...listChanges,
      },
      listChanges,
      createAListEnabled: false,
      newListName: '',
      newListValidation: CreateAListValidator.defaultState().asObject(),
    };
  }

  hasTrackingChanged() {
    return _.isEqual(this.state.originalListState, this.state.listChanges);
  }

  componentDidUpdate(prevProps: AddToListDialogProps) {
    if (prevProps.open && !this.props.open) {
      this.handleModalClose();
    } else if (!prevProps.open && this.props.open) {
      this.setState({ exited: false });
    }

    if (!prevProps.listItemAddLoading && this.props.listItemAddLoading) {
      this.setState({ actionPending: true });
    } else if (prevProps.listItemAddLoading && !this.props.listItemAddLoading) {
      this.setState({ actionPending: false, exited: true });
    }

    if (prevProps.createAListLoading && !this.props.createAListLoading) {
      this.setState({
        createAListEnabled: false,
        newListName: '',
        newListValidation: CreateAListValidator.defaultState().asObject(),
      });
    }
  }

  handleModalClose = () => {
    this.setState({ exited: true, createAListEnabled: false });
    this.props.onClose();
  };

  handleAddToList = (id: number) => {
    this.props.addToList(id.toString(), this.props.item.id.toString());
  };

  handleCheckboxChange = (list: List, checked: boolean) => {
    this.setState({
      listChanges: {
        ...this.state.listChanges,
        [list.id]: checked,
      },
    });
  };

  listContainsItem = (list: List, item: Thing) => {
    return list.things ? R.any(R.propEq('id', item.id), list.things) : false;
  };

  handleSubmit = () => {
    let addedToLists = R.filter(list => {
      return (
        this.state.listChanges[list.id] &&
        !this.listContainsItem(list, this.props.item)
      );
    }, R.values(this.props.listsById));

    let removedFromLists = R.filter(list => {
      return (
        !this.state.listChanges[list.id] &&
        this.listContainsItem(list, this.props.item)
      );
    }, R.values(this.props.listsById));

    const extractIds = R.map<List, string>(
      R.compose(
        R.toString,
        R.prop('id'),
      ),
    );

    this.props.updateLists({
      thingId: this.props.item.id,
      addToLists: extractIds(addedToLists),
      removeFromLists: extractIds(removedFromLists),
    });
    this.handleModalClose();
  };

  toggleCreateAList = () => {
    this.setState({
      createAListEnabled: !this.state.createAListEnabled,
      newListName: '',
    });
  };

  createNewList = () => {
    this.setState({
      newListValidation: CreateAListValidator.defaultState().asObject(),
    });

    let result = CreateAListValidator.validate(
      this.props.listsById,
      this.state.newListName,
    );

    if (result.hasError()) {
      this.setState({
        newListValidation: result.asObject(),
      });
    } else {
      this.props.createList({ name: this.state.newListName });
    }
  };

  updateListName = (ev: ChangeEvent<HTMLInputElement>) => {
    this.setState({
      newListName: ev.target.value,
    });
  };

  renderCreateNewListSection() {
    let {
      newListValidation: { nameDuplicateError, nameLengthError },
    } = this.state;

    return (
      <React.Fragment>
        <FormControl disabled={this.props.createAListLoading}>
          <InputLabel>New list</InputLabel>
          <Input
            type="text"
            value={this.state.newListName}
            onChange={this.updateListName}
            error={nameDuplicateError || nameLengthError}
            fullWidth
            disabled={this.props.createAListLoading}
            endAdornment={
              <React.Fragment>
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    disableRipple
                    onClick={this.toggleCreateAList}
                  >
                    <Cancel />
                  </IconButton>
                </InputAdornment>
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    onClick={this.createNewList}
                    disableRipple
                  >
                    <Check />
                  </IconButton>
                </InputAdornment>
              </React.Fragment>
            }
          />
          <FormHelperText
            id="component-error-text"
            style={{
              display: nameDuplicateError || nameLengthError ? 'block' : 'none',
            }}
          >
            {nameLengthError ? 'List name cannot be blank' : null}
            {nameDuplicateError
              ? 'You already have a list with this name'
              : null}
          </FormHelperText>
        </FormControl>
      </React.Fragment>
    );
  }

  render() {
    return (
      <Dialog
        aria-labelledby="simple-modal-title"
        aria-describedby="simple-modal-description"
        open={this.props.open && !this.state.exited}
        onClose={this.handleModalClose}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle id="simple-dialog-title">
          Add or Remove "{this.props.item.name}" from your lists
        </DialogTitle>

        <DialogContent style={{ display: 'flex' }}>
          <FormGroup>
            {_.map(this.props.listsById, list => (
              <FormControlLabel
                key={list.id}
                control={
                  <Checkbox
                    onChange={(_, checked) =>
                      this.handleCheckboxChange(list, checked)
                    }
                    checked={this.state.listChanges[list.id]}
                  />
                }
                label={list.name}
              />
            ))}
            {this.state.createAListEnabled
              ? this.renderCreateNewListSection()
              : null}
          </FormGroup>
        </DialogContent>
        <DialogActions>
          <Button
            disabled={this.state.createAListEnabled}
            onClick={this.toggleCreateAList}
          >
            New List
          </Button>
          <Button onClick={this.handleModalClose}>Cancel</Button>
          <Button
            disabled={this.hasTrackingChanged()}
            onClick={this.handleSubmit}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    listOperations: appState.lists.operation,
    listsById: appState.lists.listsById,
    listItemAddLoading: R.defaultTo(false)(
      appState.lists.loading[LIST_ADD_ITEM_INITIATED],
    ),
    createAListLoading: R.defaultTo(false)(
      appState.userSelf.loading[USER_SELF_CREATE_LIST],
    ),
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      addToList,
      updateLists: ListUpdate,
      createList,
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  )(AddToListDialog),
);
