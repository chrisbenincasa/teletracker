import React, { useState } from 'react';
import { createStyles, Theme } from '@material-ui/core';
import AddToListDialog from './Dialogs/AddToListDialog';
import AuthDialog from './Auth/AuthDialog';
import { Item } from '../types/v2/Item';
import ManageTrackingButton from './Buttons/ManageTrackingButton';
import { useWithUserContext } from '../hooks/useWithUser';
import { makeStyles } from '@material-ui/core/styles';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    button: {
      marginTop: theme.spacing(1),
      [theme.breakpoints.down('sm')]: {
        padding: theme.spacing(0.25),
      },
    },
    buttonIcon: {
      marginRight: theme.spacing(1),
      [theme.breakpoints.down('sm')]: {
        display: 'none',
      },
    },
    itemCTA: {
      width: '100%',
      whiteSpace: 'nowrap',
    },
  }),
);

interface Props {
  itemDetail: Item;
  style?: object;
  className?: string;
}

function ManageTracking(props: Props) {
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [manageTrackingModalOpen, setManageTrackingModalOpen] = useState(false);
  const userState = useWithUserContext();
  const classes = useStyles();

  const toggleLoginModal = (): void => {
    setLoginModalOpen(prev => !prev);
  };

  const openManageTrackingModal = (): void => {
    if (userState.userSelf) {
      setManageTrackingModalOpen(true);
    } else {
      toggleLoginModal();
    }
  };

  const closeManageTrackingModal = (): void => {
    setManageTrackingModalOpen(false);
  };

  return (
    <React.Fragment>
      <ManageTrackingButton
        itemDetail={props.itemDetail}
        onClick={openManageTrackingModal}
        style={classes}
        className={props.className}
      />
      <AddToListDialog
        open={manageTrackingModalOpen}
        onClose={closeManageTrackingModal}
        userSelf={userState.userSelf}
        item={props.itemDetail}
      />
      <AuthDialog open={loginModalOpen} onClose={toggleLoginModal} />
    </React.Fragment>
  );
}

export default ManageTracking;
