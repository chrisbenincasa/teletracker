import React, { CSSProperties } from 'react';
import { NetworkType, networkToPrettyName, networkToColor } from '../../types';
import { makeStyles, Theme, createStyles, Chip } from '@material-ui/core';
import { getLogoUrl } from '../../utils/image-helper';
import * as allNetworks from '../../constants/networks';

interface Props {
  readonly networkType: NetworkType;
  readonly extraStyles?: CSSProperties;
  readonly isSelected?: boolean;
  readonly onClick?: () => void;
  readonly onDelete?: () => void;
}

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    chip: {
      justifyContent: 'flex-start',
    },
    networkIconWrapper: {
      padding: '1px 5px',
      borderRadius: 8,
      width: 60,
      textAlign: 'center',
    },
  }),
);

const PerNetworkStyles: { readonly [k in NetworkType]?: CSSProperties } = {
  [allNetworks.Netflix]: {
    backgroundPosition: '62% 59%',
    backgroundSize: '95%',
  },
  [allNetworks.DisneyPlus]: { backgroundSize: '100%' },
  [allNetworks.AppleTv]: { backgroundSize: '66%' },
};

export default function NetworkChip(props: Props) {
  const classes = useStyles();
  const prettyName = networkToPrettyName[props.networkType];

  return (
    <Chip
      key={props.networkType}
      onClick={props.onClick}
      onDelete={props.onDelete}
      size="medium"
      color={props.isSelected ? 'primary' : 'secondary'}
      label={prettyName}
      className={classes.chip}
      icon={
        // Dumpy hack to get MUI to apply the right class name to the icon
        <div>
          <NetworkChipIcon
            networkType={props.networkType}
            extraStyles={props.extraStyles}
          />
        </div>
      }
    />
  );
}

interface NetworkChipIconProps {
  readonly networkType: NetworkType;
  readonly extraStyles?: CSSProperties;
}

export function NetworkChipIcon(props: NetworkChipIconProps) {
  const classes = useStyles();
  return (
    <div
      className={classes.networkIconWrapper}
      style={{
        height: 25,
        overflow: 'hidden',
        background: `${networkToColor[props.networkType]} url(${getLogoUrl(
          props.networkType,
        )}) no-repeat 50%`,
        backgroundSize: 'contain',
        backgroundOrigin: 'content-box',
        ...PerNetworkStyles[props.networkType],
        ...props.extraStyles,
      }}
    />
  );
}
