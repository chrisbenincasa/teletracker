import React, { useState, useCallback } from 'react';
import { createStyles, Theme } from '@material-ui/core';
import AddToListDialog from './Dialogs/AddToListDialog';
import AuthDialog from './Auth/AuthDialog';
import ManageTrackingButton from './Buttons/ManageTrackingButton';
import { useWithUserContext } from '../hooks/useWithUser';
import { makeStyles } from '@material-ui/core/styles';
import { Id } from '../types/v2';

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
  readonly itemId: Id;
  readonly style?: object;
  readonly className?: string;
}

function ManageTracking(props: Props) {
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [manageTrackingModalOpen, setManageTrackingModalOpen] = useState(false);
  const userState = useWithUserContext();
  const classes = useStyles();

  const toggleLoginModal = useCallback((): void => {
    setLoginModalOpen(prev => !prev);
  }, []);

  const openManageTrackingModal = useCallback(() => {
    if (userState.isLoggedIn) {
      setManageTrackingModalOpen(true);
    } else {
      toggleLoginModal();
    }
  }, [userState.isLoggedIn]);

  const closeManageTrackingModal = useCallback((): void => {
    setManageTrackingModalOpen(false);
  }, []);

  return (
    <React.Fragment>
      <ManageTrackingButton
        itemId={props.itemId}
        onClick={openManageTrackingModal}
        style={classes}
        className={props.className}
      />
      <AddToListDialog
        open={manageTrackingModalOpen}
        onClose={closeManageTrackingModal}
        itemId={props.itemId}
      />
      <AuthDialog open={loginModalOpen} onClose={toggleLoginModal} />
    </React.Fragment>
  );
}

export default ManageTracking;
