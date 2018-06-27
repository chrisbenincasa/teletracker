import * as React from 'react';

import styles from '../../Themes/ApplicationStyles';
import { Header as ElementsHeader } from 'react-native-elements';

import HeaderCenter from './HeaderCenter';
import HeaderLeft from './HeaderLeft';
import HeaderRight from './HeaderRight';

interface Props {
    title?: string
}

export default class Header extends React.PureComponent<Props> {
    render() {
        return (
            <ElementsHeader
                outerContainerStyles={{...styles.header.outer, ...this.props.outerContainerStyles}}
                innerContainerStyles={{...styles.header.inner, ...this.props.innerContainerStyles}}
                statusBarProps={{...styles.header.statusBarProps, ...this.props.statusBarProps}}
            >
                <HeaderLeft {...this.props} />
                <HeaderCenter {...this.props} />
                <HeaderRight {...this.props} />
            </ElementsHeader> 
        );
    }
}