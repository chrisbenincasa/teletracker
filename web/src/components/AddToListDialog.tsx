import {
  Button,
  Checkbox,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  FormGroup,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import {
  addToList,
  ListUpdate,
  ListUpdatedInitiatedPayload,
} from '../actions/lists';
import { AppState } from '../reducers';
import { ListOperationState, ListsByIdMap } from '../reducers/lists';
import { List, Thing, User } from '../types';
import _ from 'lodash';

const styles = (theme: Theme) =>
  createStyles({
    formControl: {
      margin: theme.spacing(3),
    },
  });

interface AddToListDialogProps {
  open: boolean;
  userSelf: User;
  item: Thing;
  listOperations: ListOperationState;
  listsById: ListsByIdMap;
}

interface AddToListDialogDispatchProps {
  addToList: (listId: string, itemId: string) => void;
  updateLists: (payload: ListUpdatedInitiatedPayload) => void;
}

interface AddToListDialogState {
  exited: boolean;
  actionPending: boolean;
  listChanges: { [listId: number]: boolean };
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
      listChanges,
    };
  }

  componentDidUpdate(prevProps: AddToListDialogProps) {
    if (prevProps.open && !this.props.open) {
      this.handleModalClose();
    } else if (!prevProps.open && this.props.open) {
      this.setState({ exited: false });
    }

    if (
      !prevProps.listOperations.inProgress &&
      this.props.listOperations.inProgress
    ) {
      this.setState({ actionPending: true });
    } else if (
      prevProps.listOperations.inProgress &&
      !this.props.listOperations.inProgress
    ) {
      this.setState({ actionPending: false, exited: true });
    }
  }

  handleModalClose = () => {
    this.setState({ exited: true });
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
  };

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
              // <ListItem
              //   button
              //   disabled={this.props.listOperations.inProgress}
              //   key={list.id}
              //   onClick={() => this.handleAddToList(list.id)}
              // >
              //   <ListItemText primary={list.name} />
              // </ListItem>
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
          </FormGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={this.handleModalClose} color="primary">
            Cancel
          </Button>
          <Button onClick={this.handleSubmit} color="primary">
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
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      addToList,
      updateLists: ListUpdate,
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  )(AddToListDialog),
);
