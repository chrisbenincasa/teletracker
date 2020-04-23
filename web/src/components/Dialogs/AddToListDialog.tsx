import React, { ChangeEvent, Component } from 'react';
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
import { Cancel, Check, PlaylistAdd } from '@material-ui/icons';
import _ from 'lodash';
import * as R from 'ramda';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import {
  addToList,
  createList,
  ListTrackingUpdatedInitiatedPayload,
  LIST_ADD_ITEM_INITIATED,
  LIST_RETRIEVE_ALL_INITIATED,
  updateListTracking,
  UserCreateListPayload,
  USER_SELF_CREATE_LIST,
} from '../../actions/lists';
import { AppState } from '../../reducers';
import { ListOperationState, ListsByIdMap } from '../../reducers/lists';
import { UserSelf } from '../../reducers/user';
import { List } from '../../types';
import { Item, itemBelongsToLists } from '../../types/v2/Item';
import CreateAListValidator, {
  CreateAListValidationStateObj,
} from '../../utils/validation/CreateAListValidator';
import AuthDialog from '../Auth/AuthDialog';

const styles = (theme: Theme) =>
  createStyles({
    dialogContainer: {
      display: 'flex',
    },
    formControl: {
      margin: theme.spacing(3),
    },
    button: {
      margin: theme.spacing(1),
      whiteSpace: 'nowrap',
    },
    leftIcon: {
      marginRight: theme.spacing(1),
    },
    spacer: {
      display: 'flex',
      flexGrow: 1,
    },
    title: {
      backgroundColor: theme.palette.primary.main,
      padding: theme.spacing(1, 2),
    },
  });

interface AddToListDialogProps {
  open: boolean;
  onClose: () => void;
  userSelf?: UserSelf;
  item: Item;
  listOperations: ListOperationState;
  listItemAddLoading: boolean;
  listLoading: boolean;
  createAListLoading: boolean;
  listsById: ListsByIdMap;
}

interface AddToListDialogDispatchProps {
  addToList: (listId: string, itemId: string) => void;
  updateLists: (payload: ListTrackingUpdatedInitiatedPayload) => void;
  createList: (payload: UserCreateListPayload) => void;
}

interface AddToListDialogState {
  actionPending: boolean;
  originalListState: { [listId: string]: boolean };
  listChanges: { [listId: string]: boolean };
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

    let listChanges = this.calculateListChanges();
    this.state = {
      actionPending: props.listOperations.inProgress,
      createAListEnabled: false,
      newListName: '',
      newListValidation: CreateAListValidator.defaultState().asObject(),
      originalListState: listChanges,
      listChanges,
    };
  }

  hasTrackingChanged() {
    return _.isEqual(this.state.originalListState, this.state.listChanges);
  }

  calculateListChanges = () => {
    const belongsToLists: string[] =
      this.props && this.props.item ? itemBelongsToLists(this.props.item) : [];

    return _.reduce(
      Object.keys(this.props.listsById),
      (acc, elem) => {
        return {
          ...acc,
          [elem]: belongsToLists.includes(elem),
        };
      },
      {},
    );
  };

  updateInitialListState = () => {
    let listChanges = this.calculateListChanges();
    this.setState({
      originalListState: {
        ...listChanges,
      },
      listChanges,
    });
  };

  componentDidMount(): void {
    if (!this.props.listLoading) {
      this.updateInitialListState();
    }
  }

  componentDidUpdate(prevProps: AddToListDialogProps) {
    if (this.props.open && !prevProps.open) {
      this.updateInitialListState();
    } else if (prevProps.open && !this.props.open) {
      this.handleModalClose();
    }

    if (!prevProps.listItemAddLoading && this.props.listItemAddLoading) {
      this.setState({ actionPending: true });
    } else if (prevProps.listItemAddLoading && !this.props.listItemAddLoading) {
      this.setState({ actionPending: false });
    }

    if (prevProps.createAListLoading && !this.props.createAListLoading) {
      this.setState({
        createAListEnabled: false,
        newListName: '',
        newListValidation: CreateAListValidator.defaultState().asObject(),
      });
    }

    if (prevProps.listLoading && !this.props.listLoading) {
      this.updateInitialListState();
    }
  }

  handleModalClose = () => {
    this.setState({ createAListEnabled: false });
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

  listContainsItem = (list: List, item: Item) => {
    return itemBelongsToLists(item).includes(list.id);
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

    const extractIds = R.map<List, string>(R.prop('id'));

    this.props.updateLists({
      itemId: this.props.item.id,
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
    const { classes, userSelf } = this.props;
    let cleanList = _.filter(
      this.props.listsById,
      item => !item.isDynamic && !item.isDeleted,
    );

    if (userSelf) {
      return (
        <Dialog
          aria-labelledby="update-tracking-dialog"
          aria-describedby="update-tracking-dialog"
          open={this.props.open}
          onClose={this.handleModalClose}
          fullWidth
          maxWidth="sm"
        >
          <DialogTitle id="update-tracking-dialog" className={classes.title}>
            Add or Remove {this.props.item.canonicalTitle} from your lists
          </DialogTitle>

          <DialogContent className={classes.dialogContainer}>
            <FormGroup>
              {_.map(cleanList, list => (
                <FormControlLabel
                  key={list.id}
                  control={
                    <Checkbox
                      onChange={(_, checked) =>
                        this.handleCheckboxChange(list, checked)
                      }
                      checked={this.state.listChanges[list.id] ? true : false}
                      color="primary"
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
              variant="contained"
              className={classes.button}
              disabled={this.state.createAListEnabled}
              onClick={this.toggleCreateAList}
            >
              <PlaylistAdd className={classes.leftIcon} />
              New List
            </Button>
            <div className={classes.spacer} />
            <Button onClick={this.handleModalClose} className={classes.button}>
              Cancel
            </Button>
            <Button
              disabled={this.hasTrackingChanged()}
              onClick={this.handleSubmit}
              color="primary"
              variant="contained"
              className={classes.button}
            >
              Save
            </Button>
          </DialogActions>
        </Dialog>
      );
    } else {
      return (
        <AuthDialog open={this.props.open} onClose={this.handleModalClose} />
      );
    }
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    listOperations: appState.lists.operation,
    listsById: appState.lists.listsById,
    listItemAddLoading: R.defaultTo(false)(
      appState.lists.loading[LIST_ADD_ITEM_INITIATED],
    ),
    listLoading: !!appState.lists.loading[LIST_RETRIEVE_ALL_INITIATED],
    createAListLoading: R.defaultTo(false)(
      appState.userSelf.loading[USER_SELF_CREATE_LIST],
    ),
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      addToList,
      updateLists: updateListTracking,
      createList,
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(mapStateToProps, mapDispatchToProps)(AddToListDialog),
);
