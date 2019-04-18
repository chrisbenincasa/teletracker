import React, { Component } from 'react';
import {
  Dialog,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
} from '@material-ui/core';
import { User } from '../types';
import { Thing } from '../types/external/themoviedb/Movie';

interface AddToListDialogProps {
  open: boolean;
  userSelf: User;
  item: Thing;
}

interface AddToListDialogState {
  open: boolean;
}

export default class AddToListDialog extends Component<
  AddToListDialogProps,
  AddToListDialogState
> {
  state = {
    open: false,
  };

  componentWillUpdate(oldProps: AddToListDialogProps) {
    if (
      this.props.open !== oldProps.open ||
      this.props.open != this.state.open
    ) {
      this.setState({ open: this.props.open });
    }
  }

  handleModalClose = () => {
    this.setState({ open: false });
  };

  render() {
    return (
      <Dialog
        aria-labelledby="simple-modal-title"
        aria-describedby="simple-modal-description"
        open={this.state.open}
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
              <ListItem button key={list.id}>
                <ListItemText primary={list.name} />
              </ListItem>
            ))}
          </List>
        </div>
      </Dialog>
    );
  }
}
