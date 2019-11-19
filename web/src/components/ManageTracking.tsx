import {
  Button,
  createStyles,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { List as ListIcon } from '@material-ui/icons';
import React, { Component } from 'react';
import AddToListDialog from '../components/AddToListDialog';
import withUser, { WithUserProps } from '../components/withUser';
import AuthDialog from './Auth/AuthDialog';
import { ApiItem } from '../types/v2';
import { Item, itemBelongsToLists } from '../types/v2/Item';
import ManageTrackingButton from './ManageTrackingButton';

const styles = (theme: Theme) =>
  createStyles({
    button: {
      marginTop: theme.spacing(1),
      width: '100% !important',
      [theme.breakpoints.down('xs')]: {
        fontSize: '0.55rem',
      },
      fontSIze: '2rem',
    },
    buttonIcon: {
      marginRight: theme.spacing(1),
      [theme.breakpoints.down('sm')]: {
        fontSize: '1rem',
      },
      fontSIze: '2rem',
    },
    itemCTA: {
      width: '100%',
      whiteSpace: 'nowrap',
    },
  });

interface OwnProps {
  itemDetail: Item;
  style: object;
}

type Props = OwnProps & WithStyles<typeof styles> & WithUserProps;

interface State {
  manageTrackingModalOpen: boolean;
  belongsToLists: boolean;
  loginModalOpen: boolean;
}

class ManageTracking extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    const belongsToLists: number[] =
      props && props.itemDetail ? itemBelongsToLists(props.itemDetail) : [];

    this.state = {
      manageTrackingModalOpen: false,
      belongsToLists: !!belongsToLists.length,
      loginModalOpen: false,
    };
  }

  toggleLoginModal = (): void => {
    this.setState({ loginModalOpen: !this.state.loginModalOpen });
  };

  openManageTrackingModal = (): void => {
    if (this.props.userSelf) {
      this.setState({ manageTrackingModalOpen: true });
    } else {
      this.toggleLoginModal();
    }
  };

  closeManageTrackingModal = (): void => {
    this.setState({ manageTrackingModalOpen: false });
  };

  renderTrackingToggle = () => {
    const { classes, style } = this.props;
    const { belongsToLists } = this.state;

    let trackingCTA = belongsToLists ? 'Manage Tracking' : 'Add to List';

    return (
      <div className={classes.itemCTA} style={{ ...style }}>
        <Button
          size="small"
          variant="contained"
          aria-label="Add to List"
          onClick={this.openManageTrackingModal}
          className={classes.button}
          startIcon={<ListIcon className={classes.buttonIcon} />}
        >
          {trackingCTA}
        </Button>
      </div>
    );
  };

  render() {
    let { manageTrackingModalOpen } = this.state;
    let { itemDetail, userSelf, style } = this.props;

    return (
      <React.Fragment>
        <ManageTrackingButton
          itemDetail={itemDetail}
          onClick={this.openManageTrackingModal}
          style={style}
        />
        <AddToListDialog
          open={manageTrackingModalOpen}
          onClose={this.closeManageTrackingModal}
          userSelf={userSelf!}
          item={itemDetail}
        />
        <AuthDialog
          open={this.state.loginModalOpen}
          onClose={this.toggleLoginModal}
        />
      </React.Fragment>
    );
  }
}

export default withUser(withStyles(styles)(ManageTracking));
