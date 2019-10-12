import {
  createStyles,
  Fab,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { Check } from '@material-ui/icons';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';
import withUser, { WithUserProps } from '../components/withUser';
import {
  removeUserItemTags,
  updateUserItemTags,
  UserUpdateItemTagsPayload,
} from '../actions/user';
import { ActionType } from '../types';
import Thing from '../types/Thing';

const styles = (theme: Theme) =>
  createStyles({
    itemCTA: {
      width: '100%',
    },
  });

interface OwnProps {
  itemDetail: Thing;
}

interface DispatchProps {
  updateUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
  removeUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
}

type Props = OwnProps &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

interface State {}

class MarkAsWatched extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {};
  }

  toggleItemWatched = () => {
    let payload = {
      thingId: this.props.itemDetail.id,
      action: ActionType.Watched,
    };

    if (!this.props.userSelf) {
      // this.props.history.push('/login');
      this.setState({
        loginModalOpen: true,
      });
    } else {
      if (this.itemMarkedAsWatched()) {
        this.props.removeUserItemTags(payload);
      } else {
        this.props.updateUserItemTags(payload);
      }
    }
  };

  itemMarkedAsWatched = () => {
    if (this.props.itemDetail) {
      return this.props.itemDetail.itemMarkedAsWatched;
    }

    return false;
  };

  render() {
    const { classes } = this.props;
    let watchedStatus = this.itemMarkedAsWatched();
    let watchedCTA = watchedStatus ? 'Mark as unwatched' : 'Mark as watched';

    return (
      <div className={classes.itemCTA}>
        <Fab
          size="small"
          variant="extended"
          aria-label="Add"
          onClick={this.toggleItemWatched}
          style={{ marginTop: 5, width: '100%' }}
          color={watchedStatus ? 'primary' : undefined}
        >
          <Check style={{ marginRight: 8 }} />
          {watchedCTA}
        </Fab>
      </div>
    );
  }
}

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      updateUserItemTags,
      removeUserItemTags,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    connect(
      null,
      mapDispatchToProps,
    )(MarkAsWatched),
  ),
);
