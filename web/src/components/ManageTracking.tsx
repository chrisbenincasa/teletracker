import {
  createStyles,
  Fab,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { List as ListIcon } from '@material-ui/icons';
import React, { Component } from 'react';
import Thing from '../types/Thing';
import AddToListDialog from '../components/AddToListDialog';
import withUser, { WithUserProps } from '../components/withUser';

const styles = (theme: Theme) =>
  createStyles({
    button: {
      marginTop: 5,
      width: '100% !important',
    },
    buttonIcon: { marginRight: 8 },
    itemCTA: {
      width: '100%',
    },
  });

interface OwnProps {
  itemDetail: Thing;
}

type Props = OwnProps & WithStyles<typeof styles> & WithUserProps;

interface State {
  manageTrackingModalOpen: boolean;
}

class ManageTracking extends Component<Props, State> {
  state: State = {
    manageTrackingModalOpen: false,
  };

  openManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: true });
  };

  closeManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: false });
  };

  renderTrackingToggle = () => {
    const { classes } = this.props;
    let trackingCTA = 'Manage Tracking';

    return (
      <div className={classes.itemCTA}>
        <Fab
          size="small"
          variant="extended"
          aria-label="Add"
          onClick={this.openManageTrackingModal}
          className={classes.button}
        >
          <ListIcon className={classes.buttonIcon} />
          {trackingCTA}
        </Fab>
      </div>
    );
  };

  render() {
    let { manageTrackingModalOpen } = this.state;
    let { itemDetail, userSelf } = this.props;

    return (
      <React.Fragment>
        {this.renderTrackingToggle()}
        <AddToListDialog
          open={manageTrackingModalOpen}
          onClose={this.closeManageTrackingModal.bind(this)}
          userSelf={userSelf!}
          item={itemDetail}
        />
      </React.Fragment>
    );
  }
}

export default withUser(withStyles(styles)(ManageTracking));
