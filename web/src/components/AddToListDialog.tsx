import {
  Dialog,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
} from '@material-ui/core';
import React, { Component } from 'react';
import { User } from '../types';
import { Thing } from '../types/external/themoviedb/Movie';
import { AppState } from '../reducers';
import { bindActionCreators } from 'redux';
import { addToList } from '../actions/lists';
import { ListOperationState } from '../reducers/lists';
import { connect } from 'react-redux';

interface AddToListDialogProps {
  open: boolean;
  userSelf: User;
  item: Thing;
  listOperations: ListOperationState;
}

interface AddToListDialogDispatchProps {
  addToList: (listId: string, itemId: string) => void;
}

interface AddToListDialogState {
  exited: boolean;
  actionPending: boolean;
}

class AddToListDialog extends Component<
  AddToListDialogProps & AddToListDialogDispatchProps,
  AddToListDialogState
> {
  constructor(props: AddToListDialogProps & AddToListDialogDispatchProps) {
    super(props);
    this.state = {
      exited: false,
      actionPending: props.listOperations.inProgress,
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

  render() {
    return (
      <Dialog
        aria-labelledby="simple-modal-title"
        aria-describedby="simple-modal-description"
        open={this.props.open && !this.state.exited}
        onClose={this.handleModalClose}
        fullWidth
        maxWidth="xs"
      >
        <DialogTitle id="simple-dialog-title">
          Add "{this.props.item.name}" to a list
        </DialogTitle>

        <div>
          <List>
            {this.props.userSelf.lists.map(list => (
              <ListItem
                button
                disabled={this.props.listOperations.inProgress}
                key={list.id}
                onClick={() => this.handleAddToList(list.id)}
              >
                <ListItemText primary={list.name} />
              </ListItem>
            ))}
          </List>
        </div>
      </Dialog>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    listOperations: appState.lists.operation,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      addToList,
    },
    dispatch,
  );

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(AddToListDialog);
